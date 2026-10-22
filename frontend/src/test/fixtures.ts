import type { Employee, Role, Shift, ScheduleDetail } from "../types";
export const employee: Employee = {
  id: "employee-alice",
  userId: "user-alice",
  firstName: "Alice",
  lastName: "Rivera",
  email: "alice@example.test",
  employeeNumber: "EMP001",
  phoneNumber: null,
  hireDate: "2026-01-05",
};
export const bob: Employee = {
  ...employee,
  id: "employee-bob",
  userId: "user-bob",
  firstName: "Bob",
  email: "bob@example.test",
  employeeNumber: "EMP002",
};
export const shift: Shift = {
  id: "shift-1",
  scheduleId: "week-1",
  employeeId: employee.id,
  shiftDate: "2026-10-26",
  startTime: "09:00:00",
  endTime: "17:00:00",
};
export const schedule: ScheduleDetail = {
  id: "week-1",
  weekStartDate: "2026-10-26",
  status: "DRAFT",
  createdAt: "2026-10-01T12:00:00Z",
  shifts: [shift],
};
export function auth(role: Role = "ADMIN", expires = Date.now() + 3600000) {
  const payload = {
    sub: role === "ADMIN" ? "user-admin" : employee.userId,
    role,
    email: role === "ADMIN" ? "admin@example.test" : employee.email,
    exp: Math.floor(expires / 1000),
  };
  return {
    role,
    token: `header.${btoa(JSON.stringify(payload))}.test-signature`,
  };
}
export const response = (body: unknown, status = 200) =>
  new Response(status === 204 ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
