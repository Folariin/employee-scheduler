import { request } from "./client";
import type {
  InterpretationResponse,
  GenerationRequest,
  GenerationPreview,
  ApplyGenerationRequest,
  AuthResponse,
  Employee,
  CreateEmployee,
  UpdateEmployee,
  WeeklySchedule,
  ScheduleDetail,
  Shift,
  ShiftRequest,
  Availability,
  AvailabilityRequest,
  DayOfWeek,
  DateRange,
} from "../types";
const rangeQuery = (range: DateRange = {}) => {
  const params = new URLSearchParams();
  if (range.startDate) params.set("startDate", range.startDate);
  if (range.endDate) params.set("endDate", range.endDate);
  return params.size ? `?${params}` : "";
};
export const api = {
  login: (email: string, password: string) =>
    request<AuthResponse>("/auth/login", "POST", { email, password }),
  employees: () => request<Employee[]>("/admin/employees"),
  employee: (id: string) =>
    request<Employee>(`/admin/employees/${encodeURIComponent(id)}`),
  createEmployee: (data: CreateEmployee) =>
    request<Employee>("/admin/employees", "POST", data),
  updateEmployee: (id: string, data: UpdateEmployee) =>
    request<Employee>(
      `/admin/employees/${encodeURIComponent(id)}`,
      "PUT",
      data,
    ),
  deleteEmployee: (id: string) =>
    request<void>(`/admin/employees/${encodeURIComponent(id)}`, "DELETE"),
  schedules: () => request<WeeklySchedule[]>("/admin/schedules"),
  schedule: (id: string) =>
    request<ScheduleDetail>(`/admin/schedules/${encodeURIComponent(id)}`),
  createSchedule: (weekStartDate: string) =>
    request<WeeklySchedule>("/admin/schedules", "POST", { weekStartDate }),
  publish: (id: string) =>
    request<WeeklySchedule>(
      `/admin/schedules/${encodeURIComponent(id)}/publish`,
      "POST",
    ),
  interpretInstructions: (id: string, instructions: string) =>
    request<InterpretationResponse>(`/admin/schedules/${encodeURIComponent(id)}/interpret`, "POST", { instructions }),
  generatePreview: (id: string, data: GenerationRequest) =>
    request<GenerationPreview>(`/admin/schedules/${encodeURIComponent(id)}/generate-preview`, "POST", data),
  applyGenerated: (id: string, data: ApplyGenerationRequest) =>
    request<Shift[]>(`/admin/schedules/${encodeURIComponent(id)}/apply-generated`, "POST", data),
  createShift: (id: string, data: ShiftRequest) =>
    request<Shift>(
      `/admin/schedules/${encodeURIComponent(id)}/shifts`,
      "POST",
      data,
    ),
  updateShift: (id: string, data: ShiftRequest) =>
    request<Shift>(`/admin/shifts/${encodeURIComponent(id)}`, "PUT", data),
  deleteShift: (id: string) =>
    request<void>(`/admin/shifts/${encodeURIComponent(id)}`, "DELETE"),
  myShifts: (range?: DateRange) =>
    request<Shift[]>(`/shifts/me${rangeQuery(range)}`),
  mySchedules: (range?: DateRange) =>
    request<ScheduleDetail[]>(`/schedules/me${rangeQuery(range)}`),
  availability: () => request<Availability[]>("/availability/me"),
  createAvailability: (data: AvailabilityRequest) =>
    request<Availability>("/availability/me", "POST", data),
  updateAvailability: (data: AvailabilityRequest) =>
    request<Availability>(`/availability/me/${data.dayOfWeek}`, "PUT", data),
  deleteAvailability: (day: DayOfWeek) =>
    request<void>(`/availability/me/${day}`, "DELETE"),
};
