import { beforeEach, it, expect, vi } from "vitest";
import { render, screen, within, fireEvent, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { GenerateSchedule } from "../features/schedules/GenerateSchedule";
import { session } from "../features/auth/session";
import { auth, response, shift } from "./fixtures";
import type { GenerationPreview } from "../types";

function preview(): GenerationPreview {
  return {
    previewToken: "flex-token", scheduleId: "week-1", expiresAt: new Date(Date.now()+600000).toISOString(),
    assignments: [
      { suggestionId: "0:a", requirementIndex: 0, employeeId: "a", displayName: "Alice Flexible", shiftDate: "2026-10-26", startTime: "09:00:00", endTime: "14:00:00" },
      { suggestionId: "0:b", requirementIndex: 0, employeeId: "b", displayName: "Bob Flexible", shiftDate: "2026-10-26", startTime: "10:00:00", endTime: "16:00:00" },
      { suggestionId: "0:c", requirementIndex: 0, employeeId: "c", displayName: "Charlie Flexible", shiftDate: "2026-10-26", startTime: "14:00:00", endTime: "17:00:00" },
    ],
    coverage: [{ mode: "FLEXIBLE", requirementIndex: 0, date: "2026-10-26", startTime: "09:00:00", endTime: "17:00:00", requiredEmployees: 3, existingEmployees: 0, suggestedEmployees: 3, shortage: 0, explanation: "Distinct employees during the window; simultaneous coverage is not guaranteed." }],
    workloads: [{ employeeId: "a", displayName: "Alice Flexible", existingWeeklyHours: 1, proposedWeeklyHours: 5, resultingWeeklyHours: 6 }],
  };
}
function mount(existing = false, saved=vi.fn()) {
  const client=new QueryClient({ defaultOptions: { queries: { retry:false }, mutations: { retry:false } } });
  render(<QueryClientProvider client={client}><MemoryRouter><GenerateSchedule scheduleId="week-1" week="2026-10-26" onClose={vi.fn()} onSaved={saved}
    existingShifts={existing?[shift]:[]} existingEmployeeNames={{ [shift.employeeId]: "Existing Worker" }} /></MemoryRouter></QueryClientProvider>);
  return saved;
}
beforeEach(()=>{session.clear();session.set(auth());});
it("switches to flexible windows, explains distinct staffing and submits duration options",async()=>{
  const fetch=vi.fn().mockResolvedValue(response(preview()));vi.stubGlobal("fetch",fetch);mount();const user=userEvent.setup();
  await user.selectOptions(screen.getByLabelText("Scheduling mode 1"),"FLEXIBLE");
  expect(screen.getByText(/does not guarantee simultaneous coverage/)).toBeVisible();expect(screen.getByLabelText("Window start 1")).toBeVisible();
  expect(screen.getByLabelText("Minimum flexible shift minutes")).toHaveValue(120);expect(screen.getByLabelText("Maximum flexible shift minutes")).toHaveValue(480);
  fireEvent.change(screen.getByLabelText("Distinct employees 1"),{target:{value:"3"}});
  await user.click(screen.getByRole("button",{name:"Generate preview"}));await screen.findByRole("heading",{name:"Review suggestions"});
  const body=JSON.parse(fetch.mock.calls[0][1].body);
  expect(body).toEqual({requirements:[{date:"2026-10-26",startTime:"09:00",endTime:"17:00",requiredEmployees:3,mode:"FLEXIBLE"}],maxWeeklyHours:40,balanceHoursFairly:true,minShiftMinutes:120,maxShiftMinutes:480});
});
it("renders staggered generated times in the preview calendar and selection updates it",async()=>{
  vi.stubGlobal("fetch",vi.fn().mockResolvedValue(response(preview())));mount(true);const user=userEvent.setup();
  await user.selectOptions(screen.getByLabelText("Scheduling mode 1"),"FLEXIBLE");await user.click(screen.getByRole("button",{name:"Generate preview"}));
  const calendar=await screen.findByRole("region",{name:/Weekly calendar/});
  expect(within(calendar).getByText("Alice Flexible")).toBeVisible();expect(within(calendar).getByText("Bob Flexible")).toBeVisible();expect(within(calendar).getByText("Charlie Flexible")).toBeVisible();
  expect(within(calendar).getByText("9:00 AM – 2:00 PM")).toBeVisible();expect(within(calendar).getByText("10:00 AM – 4:00 PM")).toBeVisible();
  expect(within(calendar).getByText("Existing Worker")).toBeVisible();expect(within(calendar).getByText("Existing shift")).toBeVisible();
  expect(within(calendar).getAllByText("Suggested shift")).toHaveLength(3);
  await user.click(screen.getByRole("checkbox",{name:/Bob Flexible/}));expect(within(calendar).queryByText("Bob Flexible")).not.toBeInTheDocument();
  expect(within(calendar).getByText("Existing Worker")).toBeVisible();
});
it("switching back to fixed keeps the original request contract and exact-time controls",async()=>{
  const fetch=vi.fn().mockResolvedValue(response({...preview(),assignments:[],coverage:[]}));vi.stubGlobal("fetch",fetch);mount();const user=userEvent.setup();
  await user.selectOptions(screen.getByLabelText("Scheduling mode 1"),"FLEXIBLE");await user.selectOptions(screen.getByLabelText("Scheduling mode 1"),"FIXED");
  expect(screen.getByLabelText("Start time 1")).toBeVisible();expect(screen.queryByLabelText("Minimum flexible shift minutes")).not.toBeInTheDocument();
  await user.click(screen.getByRole("button",{name:"Generate preview"}));await waitFor(()=>expect(fetch).toHaveBeenCalledOnce());
  const body=JSON.parse(fetch.mock.calls[0][1].body);expect(body.minShiftMinutes).toBeUndefined();expect(body.requirements[0].mode).toBeUndefined();
  expect(body.requirements[0].startTime).toBe("09:00");expect(body.requirements[0].endTime).toBe("17:00");
});
it("rejects inverted duration limits without requesting a preview",async()=>{
  const fetch=vi.fn();vi.stubGlobal("fetch",fetch);mount();const user=userEvent.setup();await user.selectOptions(screen.getByLabelText("Scheduling mode 1"),"FLEXIBLE");
  fireEvent.change(screen.getByLabelText("Minimum flexible shift minutes"),{target:{value:"181"}});fireEvent.change(screen.getByLabelText("Maximum flexible shift minutes"),{target:{value:"180"}});
  await user.click(screen.getByRole("button",{name:"Generate preview"}));expect(screen.getByRole("alert")).toHaveTextContent("minimum ≤ maximum");expect(fetch).not.toHaveBeenCalled();
});
it("shows minimum-length shortages without suggesting simultaneous coverage",async()=>{
  const data=preview();data.assignments=[];data.coverage[0].shortage=3;data.coverage[0].suggestedEmployees=0;data.coverage[0].explanation="No available interval meets the minimum of 120 minutes. Simultaneous coverage is not guaranteed.";
  vi.stubGlobal("fetch",vi.fn().mockResolvedValue(response(data)));mount();const user=userEvent.setup();await user.selectOptions(screen.getByLabelText("Scheduling mode 1"),"FLEXIBLE");
  await user.click(screen.getByRole("button",{name:"Generate preview"}));expect(await screen.findByText(/minimum of 120 minutes/)).toBeVisible();
  expect(screen.getByText(/Shortage: 3/)).toBeVisible();expect(screen.getByRole("button",{name:"Review selection"})).toBeDisabled();
});
it("applies only selected flexible suggestion IDs after confirmation",async()=>{
  const fetch=vi.fn(async(path:string)=>response(path.endsWith("generate-preview")?preview():[shift]));vi.stubGlobal("fetch",fetch);const saved=mount(false);const user=userEvent.setup();
  await user.selectOptions(screen.getByLabelText("Scheduling mode 1"),"FLEXIBLE");await user.click(screen.getByRole("button",{name:"Generate preview"}));
  await user.click(await screen.findByRole("checkbox",{name:/Charlie Flexible/}));await user.click(screen.getByRole("button",{name:"Review selection"}));
  expect(fetch).toHaveBeenCalledTimes(1);await user.click(screen.getByRole("button",{name:"Apply to draft"}));await waitFor(()=>expect(saved).toHaveBeenCalledOnce());
  expect(fetch).toHaveBeenLastCalledWith("/api/admin/schedules/week-1/apply-generated",expect.objectContaining({body:JSON.stringify({previewToken:"flex-token",suggestionIds:["0:a","0:b"]})}));
});
