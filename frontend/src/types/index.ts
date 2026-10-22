export type Role = "ADMIN" | "EMPLOYEE";
export type ScheduleStatus = "DRAFT" | "PUBLISHED";
export interface AuthResponse {
  token: string;
  role: Role;
}
export interface Employee {
  id: string;
  userId: string;
  firstName: string;
  lastName: string;
  email: string;
  employeeNumber: string;
  phoneNumber: string | null;
  hireDate: string;
}
export interface UpdateEmployee {
  firstName: string;
  lastName: string;
  phoneNumber: string | null;
  hireDate: string;
}
export interface CreateEmployee extends UpdateEmployee {
  email: string;
  employeeNumber: string;
  temporaryPassword: string;
}
export interface Shift {
  id: string;
  scheduleId: string;
  employeeId: string;
  shiftDate: string;
  startTime: string;
  endTime: string;
}
export interface ShiftRequest {
  employeeId: string;
  shiftDate: string;
  startTime: string;
  endTime: string;
}
export interface WeeklySchedule {
  id: string;
  weekStartDate: string;
  status: ScheduleStatus;
  createdAt: string;
}
export interface ScheduleDetail extends WeeklySchedule {
  shifts: Shift[];
}
export const DAYS = [
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
] as const;
export type DayOfWeek = (typeof DAYS)[number];
export interface AvailabilityRequest {
  dayOfWeek: DayOfWeek;
  startTime: string;
  endTime: string;
}
export interface Availability extends AvailabilityRequest {
  id: string;
  employeeId: string;
}
export interface DateRange {
  startDate?: string;
  endDate?: string;
}
