import type { AuthResponse, Role } from "../../types";
export interface Session extends AuthResponse {
  userId: string;
  email: string;
  expiresAt: number;
}
let current: Session | null = null;
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
      listeners.forEach((listener) => listener());
    } catch {
      throw new Error(
        "The server returned an invalid or expired session. Please sign in again.",
      );
    }
  },
};
