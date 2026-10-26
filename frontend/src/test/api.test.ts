import { beforeEach, describe, it, expect, vi } from "vitest";
import { session } from "../features/auth/session";
import { api } from "../api";
import { request, ApiError } from "../api/client";
import { auth, response } from "./fixtures";
beforeEach(() => session.clear());
describe("typed API and tab session", () => {
  it("attaches bearer tokens with tab-scoped persistence", async () => {
    session.set(auth("EMPLOYEE"));
    const fetch = vi.fn().mockImplementation(async () => response([]));
    vi.stubGlobal("fetch", fetch);
    await api.myShifts({ startDate: "2026-10-26", endDate: "2026-11-01" });
    expect(fetch).toHaveBeenCalledWith(
      "/api/shifts/me?startDate=2026-10-26&endDate=2026-11-01",
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: `Bearer ${session.get()?.token}`,
        }),
      }),
    );
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(1);
  });
  it("uses only me routes for employee data", async () => {
    session.set(auth("EMPLOYEE"));
    const fetch = vi.fn().mockImplementation(async () => response([]));
    vi.stubGlobal("fetch", fetch);
    await api.mySchedules();
    await api.availability();
    expect(fetch.mock.calls.map((call) => call[0])).toEqual([
      "/api/schedules/me",
      "/api/availability/me",
    ]);
  });
  it("clears invalid sessions on 401", async () => {
    session.set(auth());
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response({}, 401)));
    await expect(api.employees()).rejects.toMatchObject({ status: 401 });
    expect(session.get()).toBeNull();
  });
  it("does not log out for a forbidden action", async () => {
    session.set(auth());
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response({}, 403)));
    await expect(api.employees()).rejects.toMatchObject({ status: 403 });
    expect(session.get()).not.toBeNull();
  });
  it("preserves backend conflict details", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          response({ detail: "Employee is unavailable" }, 409),
        ),
    );
    await expect(api.createShift("week", {} as never)).rejects.toEqual(
      new ApiError(409, "Employee is unavailable"),
    );
  });
  it("handles no-content deletion without parsing JSON", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response(null, 204)));
    await expect(api.deleteShift("shift")).resolves.toBeUndefined();
  });
  it("reports network failures clearly", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("network")));
    await expect(request("/x")).rejects.toMatchObject({
      status: 0,
      message: expect.stringContaining("Unable to connect"),
    });
  });
  it("rejects expired or inconsistent login responses", () => {
    expect(() => session.set(auth("ADMIN", Date.now() - 1000))).toThrow(
      "expired",
    );
    expect(() => session.set({ ...auth("EMPLOYEE"), role: "ADMIN" })).toThrow(
      "invalid",
    );
    expect(session.get()).toBeNull();
  });
  it("sends matching weekday in availability PUT URL and body", async () => {
    const fetch = vi.fn().mockResolvedValue(response({}));
    vi.stubGlobal("fetch", fetch);
    const data = {
      dayOfWeek: "MONDAY" as const,
      startTime: "09:00",
      endTime: "17:00",
    };
    await api.updateAvailability(data);
    expect(fetch).toHaveBeenCalledWith(
      "/api/availability/me/MONDAY",
      expect.objectContaining({ method: "PUT", body: JSON.stringify(data) }),
    );
  });
});
