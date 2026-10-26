import { beforeEach, it, expect, vi } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { GenerateSchedule } from "../features/schedules/GenerateSchedule";
import { ScheduleDetail } from "../pages/AdminSchedules";
import { api } from "../api";
import { session } from "../features/auth/session";
import { auth, response, schedule, employee, shift } from "./fixtures";
import type { GenerationPreview } from "../types";

const preview = (): GenerationPreview => ({
  previewToken: "preview-token", scheduleId: "week-1", expiresAt: new Date(Date.now() + 600000).toISOString(),
  assignments: [
    { suggestionId: "0:alice", requirementIndex: 0, employeeId: "alice", displayName: "Alice Test", shiftDate: "2026-10-26", startTime: "09:00:00", endTime: "17:00:00" },
    { suggestionId: "0:bob", requirementIndex: 0, employeeId: "bob", displayName: "Bob Test", shiftDate: "2026-10-26", startTime: "09:00:00", endTime: "17:00:00" },
  ],
  coverage: [{ requirementIndex: 0, date: "2026-10-26", startTime: "09:00:00", endTime: "17:00:00", requiredEmployees: 3, existingEmployees: 0, suggestedEmployees: 2, shortage: 1, explanation: "Not enough available employees" }],
  workloads: [{ employeeId: "alice", displayName: "Alice Test", existingWeeklyHours: 0, proposedWeeklyHours: 8, resultingWeeklyHours: 8 }],
});
function mount(element: React.ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={client}><MemoryRouter initialEntries={["/admin/schedules/week-1"]}>{element}</MemoryRouter></QueryClientProvider>);
}
function panel(saved = vi.fn()) { mount(<GenerateSchedule scheduleId="week-1" week="2026-10-26" onClose={vi.fn()} onSaved={saved} />); return saved; }
beforeEach(() => { session.clear(); session.set(auth()); });
it("previews, highlights shortages, reviews selected assignments and applies only on confirmation", async () => {
  const fetch = vi.fn(async (path: string) => response(path.endsWith("generate-preview") ? preview() : [shift])); vi.stubGlobal("fetch", fetch);
  const saved = panel(); const user = userEvent.setup();
  fireEvent.change(screen.getByLabelText("Employees needed 1"), { target: { value: "3" } });
  await user.click(screen.getByRole("button", { name: "Generate preview" }));
  expect(await screen.findByRole("heading", { name: "Review suggestions" })).toBeVisible();
  expect(screen.getByText(/Shortage: 1/)).toBeVisible(); expect(screen.getByText("Weekly hours for the complete preview")).toBeVisible();
  expect(fetch).toHaveBeenCalledTimes(1);
  await user.click(screen.getByRole("checkbox", { name: /Bob Test/ }));
  await user.click(screen.getByRole("button", { name: "Review selection" }));
  expect(screen.getByRole("heading", { name: "Confirm selected assignments" })).toBeVisible(); expect(fetch).toHaveBeenCalledTimes(1);
  await user.click(screen.getByRole("button", { name: "Apply to draft" })); await waitFor(() => expect(saved).toHaveBeenCalledOnce());
  expect(fetch).toHaveBeenLastCalledWith("/api/admin/schedules/week-1/apply-generated", expect.objectContaining({ method: "POST", body: JSON.stringify({ previewToken: "preview-token", suggestionIds: ["0:alice"] }) }));
  expect(fetch).toHaveBeenNthCalledWith(1, "/api/admin/schedules/week-1/generate-preview", expect.objectContaining({ body: JSON.stringify({ requirements: [{ date: "2026-10-26", startTime: "09:00", endTime: "17:00", requiredEmployees: 3 }], maxWeeklyHours: 40, balanceHoursFairly: true }) }));
});
it("adds requirements and rejects overlapping intervals before any API call", async () => {
  const fetch = vi.fn(); vi.stubGlobal("fetch", fetch); panel(); const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: "Add requirement" }));
  await user.click(screen.getByRole("button", { name: "Generate preview" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("must not overlap"); expect(fetch).not.toHaveBeenCalled();
  await user.click(screen.getByRole("button", { name: "Remove requirement 2" }));
  fireEvent.change(screen.getByLabelText("End time 1"), { target: { value: "08:00" } });
  await user.click(screen.getByRole("button", { name: "Generate preview" }));
  expect(screen.getByRole("alert")).toHaveTextContent("start before end"); expect(fetch).not.toHaveBeenCalled();
});
it("shows a loading state and prevents duplicate requests", async () => {
  let resolve!: (value: Response) => void; const fetch = vi.fn(() => new Promise<Response>(r => { resolve = r; })); vi.stubGlobal("fetch", fetch);
  panel(); const user = userEvent.setup(); await user.click(screen.getByRole("button", { name: "Generate preview" }));
  expect(screen.getByRole("status")).toHaveTextContent("Finding available employees"); expect(screen.getByRole("button", { name: "Generating…" })).toBeDisabled();
  resolve(response(preview())); expect(await screen.findByRole("heading", { name: "Review suggestions" })).toBeVisible(); expect(fetch).toHaveBeenCalledTimes(1);
});
it("shows empty coverage and cannot apply when no suggestions are possible", async () => {
  const empty = preview(); empty.assignments = []; empty.workloads = []; empty.coverage[0].suggestedEmployees = 0; empty.coverage[0].shortage = 3;
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response(empty))); panel(); const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: "Generate preview" }));
  expect(await screen.findByText("No new assignments suggested")).toBeVisible(); expect(screen.getByRole("button", { name: "Review selection" })).toBeDisabled();
});
it("keeps stale apply errors actionable and allows a fresh preview", async () => {
  const fetch = vi.fn(async (path: string) => path.endsWith("generate-preview") ? response(preview()) : response({ detail: "Availability changed; generate a new preview" }, 409)); vi.stubGlobal("fetch", fetch);
  const saved = panel(); const user = userEvent.setup(); await user.click(screen.getByRole("button", { name: "Generate preview" }));
  await user.click(await screen.findByRole("button", { name: "Review selection" })); await user.click(screen.getByRole("button", { name: "Apply to draft" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Availability changed"); expect(saved).not.toHaveBeenCalled();
  await user.click(screen.getByRole("button", { name: "Edit requirements" })); expect(screen.getByLabelText("Date 1")).toBeVisible();
  await user.click(screen.getByRole("button", { name: "Generate preview" })); expect(await screen.findByRole("heading", { name: "Review suggestions" })).toBeVisible();
});
it("does not submit an expired preview", async () => {
  const expired = preview(); expired.expiresAt = new Date(Date.now() - 1000).toISOString();
  const fetch = vi.fn().mockResolvedValue(response(expired)); vi.stubGlobal("fetch", fetch); panel(); const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: "Generate preview" })); await user.click(await screen.findByRole("button", { name: "Review selection" }));
  await user.click(screen.getByRole("button", { name: "Apply to draft" })); expect(screen.getByRole("alert")).toHaveTextContent("Preview expired"); expect(fetch).toHaveBeenCalledTimes(1);
});
it("published schedule pages have no generation control", async () => {
  vi.stubGlobal("fetch", vi.fn(async (path: string) => response(path.endsWith("/employees") ? [employee] : path.endsWith("/schedules") ? [{ ...schedule, status: "PUBLISHED" }] : { ...schedule, status: "PUBLISHED" })));
  mount(<Routes><Route path="/admin/schedules/:id" element={<ScheduleDetail />} /></Routes>);
  expect(await screen.findByText(/published and read-only/)).toBeVisible(); expect(screen.queryByRole("button", { name: "Generate schedule" })).not.toBeInTheDocument();
});
it("API contract preserves encoded schedule IDs and sends no arbitrary assignments on apply", async () => {
  const fetch = vi.fn().mockResolvedValue(response([])); vi.stubGlobal("fetch", fetch);
  await api.applyGenerated("week/1", { previewToken: "opaque", suggestionIds: ["0:alice"] });
  expect(fetch).toHaveBeenCalledWith("/api/admin/schedules/week%2F1/apply-generated", expect.objectContaining({ method: "POST", body: JSON.stringify({ previewToken: "opaque", suggestionIds: ["0:alice"] }) }));
});

it("applying from the draft week refreshes the existing weekly calendar", async () => {
  let applied = false;
  const proposed = preview(); proposed.assignments = [{ ...proposed.assignments[0], employeeId: employee.id, displayName: "Alice Rivera" }];
  const fetch = vi.fn(async (path: string) => {
    if (path.endsWith("generate-preview")) return response(proposed);
    if (path.endsWith("apply-generated")) { applied = true; return response([shift]); }
    if (path.endsWith("/employees")) return response([employee]);
    if (path.endsWith("/schedules")) return response([schedule]);
    return response({ ...schedule, shifts: applied ? [shift] : [] });
  }); vi.stubGlobal("fetch", fetch);
  mount(<Routes><Route path="/admin/schedules/:id" element={<ScheduleDetail />} /></Routes>);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: "Generate schedule" }));
  await user.click(screen.getByRole("button", { name: "Generate preview" }));
  await user.click(await screen.findByRole("button", { name: "Review selection" }));
  await user.click(screen.getByRole("button", { name: "Apply to draft" }));
  expect(await screen.findByText("Generated assignments applied to draft. Review the calendar before publishing.")).toBeVisible();
  expect((await screen.findAllByRole("button", { name: /Edit Alice Rivera,/ }))[0]).toBeVisible();
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(fetch.mock.calls.filter(call => call[0] === "/api/admin/schedules/week-1").length).toBeGreaterThan(1);
});
