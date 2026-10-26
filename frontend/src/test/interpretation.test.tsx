import { beforeEach, it, expect, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { GenerateSchedule } from "../features/schedules/GenerateSchedule";
import { session } from "../features/auth/session";
import { auth, response, shift } from "./fixtures";
import type { InterpretationResponse, GenerationPreview } from "../types";
const interpreted: InterpretationResponse = {
 requirements: [{date:"2026-10-27",startTime:"09:00:00",endTime:"17:00:00",requiredEmployees:2,mode:"FLEXIBLE"}],
 maxWeeklyHours:null,balanceHoursFairly:null,clarificationQuestions:[],unsupportedConstraints:[],summary:"Two distinct employees Tuesday.",
};
const preview: GenerationPreview = {previewToken:"ai-preview",scheduleId:"week-1",expiresAt:new Date(Date.now()+600000).toISOString(),
 assignments:[{suggestionId:"0:a",requirementIndex:0,employeeId:"a",displayName:"Alice",shiftDate:"2026-10-27",startTime:"09:00:00",endTime:"17:00:00"}],coverage:[],workloads:[]};
function mount(){const saved=vi.fn();render(<QueryClientProvider client={new QueryClient({defaultOptions:{mutations:{retry:false},queries:{retry:false}}})}><GenerateSchedule scheduleId="week-1" week="2026-10-26" onClose={vi.fn()} onSaved={saved}/></QueryClientProvider>);return saved;}
async function enter(){const user=userEvent.setup();await user.selectOptions(screen.getByLabelText("Requirement entry"),"ai");fireEvent.change(screen.getByLabelText("Scheduling instructions"),{target:{value:"Two employees Tuesday 9–17, different shifts"}});await user.click(screen.getByRole("button",{name:"Interpret instructions"}));return user;}
beforeEach(()=>{session.clear();session.set(auth());});
it("interprets, reviews, generates and applies only after separate explicit confirmations",async()=>{
 const fetch=vi.fn(async(path:string)=>response(path.endsWith("interpret")?interpreted:path.endsWith("generate-preview")?preview:[shift]));vi.stubGlobal("fetch",fetch);const saved=mount();const user=await enter();
 await screen.findByText(interpreted.summary);expect(fetch).toHaveBeenCalledOnce();expect(fetch.mock.calls[0][0]).toBe("/api/admin/schedules/week-1/interpret");
 expect(screen.getByText(/No weekly hours cap was specified/)).toBeVisible();expect(screen.getByLabelText("Scheduling mode 1")).toHaveValue("FLEXIBLE");
 expect(screen.getByRole("button",{name:"Generate preview"})).toBeDisabled();await user.click(screen.getByRole("checkbox",{name:/I reviewed/}));
 await user.click(screen.getByRole("button",{name:"Generate preview"}));await screen.findByRole("heading",{name:"Review suggestions"});expect(fetch).toHaveBeenCalledTimes(2);
 await user.click(screen.getByRole("button",{name:"Review selection"}));expect(fetch).toHaveBeenCalledTimes(2);expect(saved).not.toHaveBeenCalled();
 await user.click(screen.getByRole("button",{name:"Apply to draft"}));await waitFor(()=>expect(saved).toHaveBeenCalledOnce());expect(fetch.mock.calls[2][0]).toBe("/api/admin/schedules/week-1/apply-generated");
});
it("shows questions and unsupported constraints without inventing missing requirements",async()=>{
 vi.stubGlobal("fetch",vi.fn().mockResolvedValue(response({...interpreted,requirements:[],clarificationQuestions:["What count and mode?"],unsupportedConstraints:["Continuous coverage is unsupported"]})));mount();const user=await enter();
 expect(await screen.findByText("What count and mode?")).toBeVisible();expect(screen.getByText("Continuous coverage is unsupported")).toBeVisible();expect(screen.queryByLabelText("Date 1")).not.toBeInTheDocument();
 await user.click(screen.getByRole("checkbox",{name:/I reviewed/}));expect(screen.getByRole("button",{name:"Generate preview"})).toBeDisabled();
});
it("edits invalidate review and instruction changes require a new interpretation",async()=>{
 const fetch=vi.fn().mockResolvedValue(response(interpreted));vi.stubGlobal("fetch",fetch);mount();const user=await enter();await screen.findByText(interpreted.summary);
 await user.click(screen.getByRole("checkbox",{name:/I reviewed/}));fireEvent.change(screen.getByLabelText("Distinct employees 1"),{target:{value:"3"}});expect(screen.getByRole("button",{name:"Generate preview"})).toBeDisabled();
 await user.click(screen.getByRole("checkbox",{name:/I reviewed/}));fireEvent.change(screen.getByLabelText("Scheduling instructions"),{target:{value:"Revised request"}});expect(screen.getByRole("button",{name:"Generate preview"})).toBeDisabled();expect(fetch).toHaveBeenCalledOnce();
});
it("provider error leaves manual entry available without making a preview",async()=>{
 const fetch=vi.fn().mockResolvedValue(response({detail:"AI provider rate limit reached"},429));vi.stubGlobal("fetch",fetch);mount();const user=await enter();expect(await screen.findByRole("alert")).toHaveTextContent("AI provider rate limit reached");expect(fetch).toHaveBeenCalledOnce();
 await user.selectOptions(screen.getByLabelText("Requirement entry"),"manual");expect(screen.getByLabelText("Date 1")).toBeVisible();expect(screen.getByRole("button",{name:"Generate preview"})).toBeEnabled();
});
it("explicit weekly cap and fairness populate the editable form",async()=>{
 vi.stubGlobal("fetch",vi.fn().mockResolvedValue(response({...interpreted,maxWeeklyHours:35,balanceHoursFairly:false})));mount();await enter();await screen.findByText(interpreted.summary);expect(screen.getByLabelText("Maximum weekly hours per employee")).toHaveValue(35);expect(screen.getByRole("checkbox",{name:"Balance hours fairly"})).not.toBeChecked();
});
