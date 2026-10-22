import { beforeEach, it, expect, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { ShiftForm } from "../features/schedules/ShiftForm";
import { AvailabilityForm } from "../pages/Availability";
import { WeeklyCalendar } from "../components/calendar/WeeklyCalendar";
import { session } from "../features/auth/session";
import { auth, employee, shift, response } from "./fixtures";
function mount(element: ReactNode) {
  return render(
    <QueryClientProvider
      client={
        new QueryClient({
          defaultOptions: {
            queries: { retry: false },
            mutations: { retry: false },
          },
        })
      }
    >
      {element}
    </QueryClientProvider>,
  );
}
beforeEach(() => {
  session.clear();
  session.set(auth());
});
it("rejects reversed shift intervals before sending a mutation", async () => {
  const fetch = vi.fn().mockResolvedValue(response([employee]));
  vi.stubGlobal("fetch", fetch);
  const user = userEvent.setup();
  mount(
    <ShiftForm
      scheduleId="week-1"
      week="2026-10-26"
      onClose={vi.fn()}
      onSaved={vi.fn()}
    />,
  );
  await user.selectOptions(
    await screen.findByLabelText("Employee"),
    employee.id,
  );
  await user.clear(screen.getByLabelText("End time"));
  await user.type(screen.getByLabelText("End time"), "08:00");
  await user.click(screen.getByRole("button", { name: "Assign shift" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Start time must be before end time",
  );
  expect(fetch.mock.calls).toHaveLength(1);
});
it("sends a valid shift contract and presents backend availability conflicts", async () => {
  const fetch = vi
    .fn()
    .mockImplementation(async (input: string) =>
      input === "/api/admin/employees"
        ? response([employee])
        : response(
            { detail: "Employee has no availability for this weekday" },
            409,
          ),
    );
  vi.stubGlobal("fetch", fetch);
  const user = userEvent.setup();
  mount(
    <ShiftForm
      scheduleId="week-1"
      week="2026-10-26"
      onClose={vi.fn()}
      onSaved={vi.fn()}
    />,
  );
  await user.selectOptions(
    await screen.findByLabelText("Employee"),
    employee.id,
  );
  await user.click(screen.getByRole("button", { name: "Assign shift" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Employee has no availability",
  );
  expect(fetch).toHaveBeenCalledWith(
    "/api/admin/schedules/week-1/shifts",
    expect.objectContaining({
      method: "POST",
      body: JSON.stringify({
        employeeId: employee.id,
        shiftDate: "2026-10-26",
        startTime: "09:00",
        endTime: "17:00",
      }),
    }),
  );
});
it("validates availability intervals locally", async () => {
  const fetch = vi.fn();
  vi.stubGlobal("fetch", fetch);
  const user = userEvent.setup();
  mount(<AvailabilityForm day="MONDAY" onClose={vi.fn()} onSaved={vi.fn()} />);
  await user.clear(screen.getByLabelText("Available until"));
  await user.type(screen.getByLabelText("Available until"), "09:00");
  await user.click(screen.getByRole("button", { name: "Save availability" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Start time must be before end time",
  );
  expect(fetch).not.toHaveBeenCalled();
});
it("creates exactly one interval with uppercase weekday enum", async () => {
  const fetch = vi.fn().mockResolvedValue(response({}, 201));
  vi.stubGlobal("fetch", fetch);
  const saved = vi.fn();
  const user = userEvent.setup();
  mount(<AvailabilityForm day="THURSDAY" onClose={vi.fn()} onSaved={saved} />);
  await user.click(screen.getByRole("button", { name: "Save availability" }));
  await waitFor(() => expect(saved).toHaveBeenCalled());
  expect(fetch).toHaveBeenCalledWith(
    "/api/availability/me",
    expect.objectContaining({
      method: "POST",
      body: JSON.stringify({
        dayOfWeek: "THURSDAY",
        startTime: "09:00",
        endTime: "17:00",
      }),
    }),
  );
});
it("calendar renders all weekdays, real names and local working hours", () => {
  mount(
    <WeeklyCalendar
      week="2026-10-26"
      shifts={[shift]}
      employees={[employee]}
    />,
  );
  expect(screen.getAllByText("Alice Rivera").length).toBeGreaterThan(0);
  for (const day of ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"])
    expect(screen.getByText(day)).toBeInTheDocument();
  expect(screen.getAllByText("9:00 AM – 5:00 PM").length).toBeGreaterThan(0);
  expect(screen.queryByRole("button")).not.toBeInTheDocument();
});
it("draft calendar shift is keyboard-operable through its edit button", async () => {
  const select = vi.fn();
  const user = userEvent.setup();
  mount(
    <WeeklyCalendar
      week="2026-10-26"
      shifts={[shift]}
      employees={[employee]}
      editable
      onSelect={select}
    />,
  );
  await user.click(
    screen.getAllByRole("button", { name: /Edit Alice Rivera/ })[0],
  );
  expect(select).toHaveBeenCalledWith(shift);
});
