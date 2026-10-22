import { beforeEach, it, expect, vi } from "vitest";
import { render, screen, within, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";
import { Employees } from "../pages/Employees";
import { Availability } from "../pages/Availability";
import { CreateSchedule } from "../pages/AdminSchedules";
import { SessionLifecycle } from "../features/auth/Auth";
import { session } from "../features/auth/session";
import { auth, employee, response } from "./fixtures";
function mount(element: ReactNode) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return {
    client,
    ...render(
      <QueryClientProvider client={client}>
        <MemoryRouter>{element}</MemoryRouter>
      </QueryClientProvider>,
    ),
  };
}
beforeEach(() => {
  session.clear();
  session.set(auth());
});
it("creates an employee without retaining or displaying their temporary password", async () => {
  let created = false;
  const fetch = vi.fn(async (_input: string, options?: RequestInit) => {
    if (options?.method === "POST") {
      created = true;
      return response(employee, 201);
    }
    return response(created ? [employee] : []);
  });
  vi.stubGlobal("fetch", fetch);
  const user = userEvent.setup();
  mount(<Employees />);
  await user.click(screen.getByRole("button", { name: "Add employee" }));
  await user.type(screen.getByLabelText("First name"), "Alice");
  await user.type(screen.getByLabelText("Last name"), "Rivera");
  await user.type(screen.getByLabelText("Email address"), "alice@example.test");
  await user.type(screen.getByLabelText("Employee number"), "EMP001");
  await user.type(screen.getByLabelText("Hire date"), "2026-01-05");
  await user.type(
    screen.getByLabelText(/Temporary password/),
    "test-only-password",
  );
  await user.click(screen.getByRole("button", { name: "Create employee" }));
  expect(await screen.findByText("Employee account created.")).toBeVisible();
  expect(screen.queryByLabelText(/Temporary password/)).not.toBeInTheDocument();
  expect(screen.queryByText("test-only-password")).not.toBeInTheDocument();
  expect(fetch).toHaveBeenCalledWith(
    "/api/admin/employees",
    expect.objectContaining({
      method: "POST",
      body: JSON.stringify({
        firstName: "Alice",
        lastName: "Rivera",
        phoneNumber: null,
        hireDate: "2026-01-05",
        email: "alice@example.test",
        employeeNumber: "EMP001",
        temporaryPassword: "test-only-password",
      }),
    }),
  );
});
it("edits only fields supported by the employee update DTO", async () => {
  let updated = false;
  const fetch = vi.fn(async (_input: string, options?: RequestInit) => {
    if (options?.method === "PUT") {
      updated = true;
      return response({ ...employee, firstName: "Alicia" });
    }
    return response([{ ...employee, firstName: updated ? "Alicia" : "Alice" }]);
  });
  vi.stubGlobal("fetch", fetch);
  const user = userEvent.setup();
  mount(<Employees />);
  await user.click(
    await screen.findByRole("button", { name: "Edit Alice Rivera" }),
  );
  expect(screen.getByLabelText("Email address")).toBeDisabled();
  expect(screen.getByLabelText("Employee number")).toBeDisabled();
  await user.clear(screen.getByLabelText("First name"));
  await user.type(screen.getByLabelText("First name"), "Alicia");
  await user.click(screen.getByRole("button", { name: "Save changes" }));
  expect(await screen.findByText("Employee updated.")).toBeVisible();
  expect(fetch).toHaveBeenCalledWith(
    "/api/admin/employees/employee-alice",
    expect.objectContaining({
      method: "PUT",
      body: JSON.stringify({
        firstName: "Alicia",
        lastName: "Rivera",
        phoneNumber: null,
        hireDate: "2026-01-05",
      }),
    }),
  );
});
it("requires confirmation before employee deletion and refreshes the list", async () => {
  let removed = false;
  const fetch = vi.fn(async (_input: string, options?: RequestInit) => {
    if (options?.method === "DELETE") {
      removed = true;
      return response(null, 204);
    }
    return response(removed ? [] : [employee]);
  });
  vi.stubGlobal("fetch", fetch);
  const user = userEvent.setup();
  mount(<Employees />);
  await user.click(
    await screen.findByRole("button", { name: "Delete Alice Rivera" }),
  );
  expect(removed).toBe(false);
  const dialog = screen.getByRole("dialog");
  expect(dialog).toHaveTextContent("user account");
  await user.click(
    within(dialog).getByRole("button", { name: "Delete employee" }),
  );
  expect(
    await screen.findByText("Employee and associated user account deleted."),
  ).toBeVisible();
  expect(removed).toBe(true);
  expect(await screen.findByText("Your team starts here")).toBeVisible();
});
it("availability editor updates an existing interval with matching weekday", async () => {
  const record = {
    id: "availability-1",
    employeeId: employee.id,
    dayOfWeek: "MONDAY",
    startTime: "09:00:00",
    endTime: "17:00:00",
  };
  const fetch = vi.fn(async (_input: string, options?: RequestInit) =>
    options?.method === "PUT" ? response(record) : response([record]),
  );
  vi.stubGlobal("fetch", fetch);
  const user = userEvent.setup();
  mount(<Availability />);
  await user.click(
    await screen.findByRole("button", { name: "Edit Monday availability" }),
  );
  await user.clear(screen.getByLabelText("Available until"));
  await user.type(screen.getByLabelText("Available until"), "18:00");
  await user.click(screen.getByRole("button", { name: "Save availability" }));
  expect(
    await screen.findByText("Recurring availability saved."),
  ).toBeVisible();
  expect(fetch).toHaveBeenCalledWith(
    "/api/availability/me/MONDAY",
    expect.objectContaining({
      method: "PUT",
      body: JSON.stringify({
        dayOfWeek: "MONDAY",
        startTime: "09:00:00",
        endTime: "18:00",
      }),
    }),
  );
});
it("availability removal confirms that assigned shifts remain unchanged", async () => {
  const record = {
    id: "availability-1",
    employeeId: employee.id,
    dayOfWeek: "MONDAY",
    startTime: "09:00:00",
    endTime: "17:00:00",
  };
  let removed = false;
  const fetch = vi.fn(async (_input: string, options?: RequestInit) => {
    if (options?.method === "DELETE") {
      removed = true;
      return response(null, 204);
    }
    return response(removed ? [] : [record]);
  });
  vi.stubGlobal("fetch", fetch);
  const user = userEvent.setup();
  mount(<Availability />);
  await user.click(
    await screen.findByRole("button", { name: "Remove Monday availability" }),
  );
  expect(removed).toBe(false);
  const dialog = screen.getByRole("dialog");
  expect(dialog).toHaveTextContent(
    "Already assigned shifts will not be changed",
  );
  await user.click(
    within(dialog).getByRole("button", { name: "Remove availability" }),
  );
  expect(
    await screen.findByText("Recurring availability removed."),
  ).toBeVisible();
  expect(fetch).toHaveBeenCalledWith(
    "/api/availability/me/MONDAY",
    expect.objectContaining({ method: "DELETE" }),
  );
});
it("schedule creation rejects a non-Monday before contacting the backend", async () => {
  const fetch = vi.fn();
  vi.stubGlobal("fetch", fetch);
  const user = userEvent.setup();
  mount(<CreateSchedule onClose={vi.fn()} />);
  await user.clear(screen.getByLabelText("Week starting Monday"));
  await user.type(screen.getByLabelText("Week starting Monday"), "2026-10-27");
  await user.click(screen.getByRole("button", { name: "Create schedule" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Schedules must start on a Monday",
  );
  expect(fetch).not.toHaveBeenCalled();
});
it("schedule creation displays the backend duplicate-week conflict", async () => {
  vi.stubGlobal(
    "fetch",
    vi
      .fn()
      .mockResolvedValue(
        response({ detail: "A schedule already exists for this week" }, 409),
      ),
  );
  const user = userEvent.setup();
  mount(<CreateSchedule onClose={vi.fn()} />);
  await user.click(screen.getByRole("button", { name: "Create schedule" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "A schedule already exists for this week",
  );
});
it("expires sessions and clears private query data without persistent storage", () => {
  vi.useFakeTimers();
  try {
    session.set(auth("ADMIN", Date.now() + 2000));
    const { client } = mount(<SessionLifecycle />);
    client.setQueryData(["private"], [employee]);
    act(() => {
      vi.advanceTimersByTime(2100);
    });
    expect(session.get()).toBeNull();
    expect(client.getQueryData(["private"])).toBeUndefined();
  } finally {
    vi.useRealTimers();
  }
});
it("focus checks cannot leave a stale timer that logs out a newer session", () => {
  vi.useFakeTimers();
  try {
    session.set(auth("ADMIN", Date.now() + 2000));
    mount(<SessionLifecycle />);
    act(() => {
      window.dispatchEvent(new Event("focus"));
    });
    act(() => {
      session.set(auth("EMPLOYEE", Date.now() + 10000));
    });
    act(() => {
      vi.advanceTimersByTime(2100);
    });
    expect(session.get()?.role).toBe("EMPLOYEE");
  } finally {
    vi.useRealTimers();
  }
});
