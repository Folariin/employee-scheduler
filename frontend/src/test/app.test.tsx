import { beforeEach, it, expect, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { AppRoutes } from "../App";
import { session } from "../features/auth/session";
import { auth, response, employee, bob, schedule, shift } from "./fixtures";
function mount(path: string) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return {
    client,
    ...render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={[path]}>
          <AppRoutes />
        </MemoryRouter>
      </QueryClientProvider>,
    ),
  };
}
beforeEach(() => session.clear());
function backend() {
  const fetch = vi.fn(async (input: string) => {
    const url = input.split("?")[0];
    if (url === "/api/admin/employees") return response([employee, bob]);
    if (url === "/api/admin/schedules") return response([schedule]);
    if (url === "/api/admin/schedules/week-1") return response(schedule);
    if (url === "/api/shifts/me") return response([shift]);
    if (url === "/api/schedules/me")
      return response([{ ...schedule, status: "PUBLISHED" }]);
    if (url === "/api/availability/me") return response([]);
    return response({}, 404);
  });
  vi.stubGlobal("fetch", fetch);
  return fetch;
}
it("redirects unauthenticated protected pages to login", async () => {
  mount("/admin/employees");
  expect(
    await screen.findByRole("heading", { name: "Good to see you." }),
  ).toBeVisible();
});
it("logs in with real contract and renders admin navigation", async () => {
  const fetch = backend();
  fetch.mockImplementation(async (input: string) =>
    input === "/api/auth/login"
      ? response(auth())
      : input === "/api/admin/employees"
        ? response([])
        : response([]),
  );
  const user = userEvent.setup();
  mount("/login");
  await user.type(screen.getByLabelText("Email address"), "admin@example.test");
  await user.type(screen.getByLabelText("Password"), "private-password");
  await user.click(
    screen.getByRole("button", { name: "Sign in to workspace" }),
  );
  expect(
    await screen.findByRole("heading", { name: "A good week starts here." }),
  ).toBeVisible();
  expect(screen.getByRole("navigation").textContent).toContain("Employees");
  expect(screen.queryByText("My Availability")).not.toBeInTheDocument();
  expect(fetch).toHaveBeenCalledWith(
    "/api/auth/login",
    expect.objectContaining({
      body: JSON.stringify({
        email: "admin@example.test",
        password: "private-password",
      }),
    }),
  );
});
it("shows invalid credential feedback without a session", async () => {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response({}, 401)));
  const user = userEvent.setup();
  mount("/login");
  await user.type(screen.getByLabelText("Email address"), "alice@example.test");
  await user.type(screen.getByLabelText("Password"), "wrong");
  await user.click(
    screen.getByRole("button", { name: "Sign in to workspace" }),
  );
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Email or password is incorrect",
  );
  expect(session.get()).toBeNull();
});
it("employee cannot enter admin routes or see admin navigation", async () => {
  backend();
  session.set(auth("EMPLOYEE"));
  mount("/admin/employees");
  expect(
    await screen.findByRole("heading", { name: "Welcome to your workspace." }),
  ).toBeVisible();
  const nav = screen.getByRole("navigation");
  expect(nav).toHaveTextContent("My Dashboard");
  expect(nav).toHaveTextContent("My Schedule");
  expect(nav).toHaveTextContent("My Availability");
  expect(nav).not.toHaveTextContent("Employees");
});
it("admin cannot enter employee-only pages", async () => {
  backend();
  session.set(auth());
  mount("/employee/availability");
  expect(
    await screen.findByRole("heading", { name: "A good week starts here." }),
  ).toBeVisible();
});
it("logout clears cached private data and returns to login", async () => {
  backend();
  session.set(auth());
  const user = userEvent.setup();
  const { client } = mount("/admin/employees");
  await screen.findByRole("button", { name: "View Alice Rivera" });
  await user.click(screen.getByRole("button", { name: "Sign out" }));
  expect(
    await screen.findByRole("heading", { name: "Good to see you." }),
  ).toBeVisible();
  expect(session.get()).toBeNull();
  expect(client.getQueryCache().getAll()).toHaveLength(0);
});
it("renders published schedules as read-only", async () => {
  const fetch = backend();
  fetch.mockImplementation(async (input: string) =>
    input === "/api/admin/schedules/week-1"
      ? response({ ...schedule, status: "PUBLISHED" })
      : input === "/api/admin/employees"
        ? response([employee])
        : response([{ ...schedule, status: "PUBLISHED" }]),
  );
  session.set(auth());
  mount("/admin/schedules/week-1");
  expect(
    await screen.findByText(/This schedule is published and read-only/),
  ).toBeVisible();
  expect(
    screen.queryByRole("button", { name: "Assign shift" }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "Publish schedule" }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: /Edit Alice/ }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: /Delete shift/ }),
  ).not.toBeInTheDocument();
  expect(screen.getAllByText("Alice Rivera").length).toBeGreaterThan(0);
});
it("publishing confirms irreversible effects then refreshes to read-only", async () => {
  let published = false;
  const fetch = backend();
  fetch.mockImplementation(async (input: string, options?: RequestInit) => {
    if (input.endsWith("/publish") && options?.method === "POST") {
      published = true;
      return response({ ...schedule, status: "PUBLISHED" });
    }
    if (input === "/api/admin/employees") return response([employee]);
    const data = { ...schedule, status: published ? "PUBLISHED" : "DRAFT" };
    return response(input === "/api/admin/schedules" ? [data] : data);
  });
  session.set(auth());
  const user = userEvent.setup();
  mount("/admin/schedules/week-1");
  await user.click(
    await screen.findByRole("button", { name: "Publish schedule" }),
  );
  const dialog = screen.getByRole("dialog");
  expect(dialog).toHaveTextContent("no further shift changes");
  await user.click(
    within(dialog).getByRole("button", { name: "Publish schedule" }),
  );
  expect(
    await screen.findByText(/This schedule is published and read-only/),
  ).toBeVisible();
  expect(
    screen.queryByRole("button", { name: "Assign shift" }),
  ).not.toBeInTheDocument();
  expect(published).toBe(true);
});
it("employee schedule uses only scoped responses and never fetches admin assignments", async () => {
  const fetch = backend();
  session.set(auth("EMPLOYEE"));
  mount("/employee/schedule");
  await waitFor(() =>
    expect(screen.getAllByText("Assigned shift").length).toBeGreaterThan(0),
  );
  expect(screen.queryByText("Bob Rivera")).not.toBeInTheDocument();
  expect(
    fetch.mock.calls.every((call) => !String(call[0]).includes("/admin/")),
  ).toBe(true);
  expect(
    fetch.mock.calls.some((call) =>
      String(call[0]).startsWith("/api/schedules/me"),
    ),
  ).toBe(true);
});
it("shows loading and API error states", async () => {
  let finish!: (response: Response) => void;
  vi.stubGlobal(
    "fetch",
    vi.fn(
      () =>
        new Promise<Response>((resolve) => {
          finish = resolve;
        }),
    ),
  );
  session.set(auth());
  mount("/admin/employees");
  expect(screen.getByRole("status")).toHaveTextContent("Loading");
  finish(response({ message: "Service temporarily unavailable" }, 503));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Service temporarily unavailable",
  );
});
