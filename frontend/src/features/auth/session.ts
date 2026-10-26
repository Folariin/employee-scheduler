import type { AuthResponse, Role } from "../../types";
export interface Session extends AuthResponse {
  userId: string;
  email: string;
  expiresAt: number;
}
export const SESSION_KEY = "employee-scheduler.session.v1";
let current: Session | null = null;
const removeStored = () => { try { sessionStorage.removeItem(SESSION_KEY); } catch { /* Storage may be disabled. */ } };
const listeners = new Set<() => void>();
export const session = {
  get: () => current,
  subscribe(listener: () => void) {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  },
  clear() {
    current = null;
    removeStored();
    listeners.forEach((listener) => listener());
  },
  set(response: AuthResponse) {
    try {
      const payload = JSON.parse(
        atob(
          response.token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/"),
        ),
      ) as { sub?: string; role?: Role; email?: string; exp?: number };
      if (
        response.token.split(".").length !== 3 ||
        typeof payload.sub !== "string" ||
        !payload.sub ||
        !payload.exp ||
        !Number.isFinite(payload.exp) ||
        payload.exp * 1000 <= Date.now() ||
        !["ADMIN", "EMPLOYEE"].includes(response.role) ||
        payload.role !== response.role
      )
        throw new Error();
      current = {
        ...response,
        userId: payload.sub,
        email: payload.email || "",
        expiresAt: payload.exp * 1000,
      };
      try { sessionStorage.setItem(SESSION_KEY, JSON.stringify({ token: response.token, role: response.role })); } catch { /* Fall back to memory if storage is blocked. */ }
      listeners.forEach((listener) => listener());
    } catch {
      throw new Error(
        "The server returned an invalid or expired session. Please sign in again.",
      );
    }
  },
};

// Synchronous hydration happens before React renders protected routes.
export function restoreSession() {
  current = null;
  try {
    const stored = sessionStorage.getItem(SESSION_KEY);
    if (stored) session.set(JSON.parse(stored) as AuthResponse);
  } catch { removeStored(); }
}
restoreSession();
