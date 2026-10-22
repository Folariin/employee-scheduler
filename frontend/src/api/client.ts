import { session } from "../features/auth/session";
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}
const fallback: Record<number, string> = {
  400: "Check the form values and try again.",
  401: "Your session has expired. Please sign in again.",
  403: "You do not have access to this action.",
  404: "This record no longer exists. Refresh and try again.",
  409: "This change conflicts with the current data. Refresh and check availability, overlaps, or published status.",
  500: "The server could not complete this request. Try again later.",
};
export async function request<T>(
  path: string,
  method = "GET",
  body?: unknown,
  signal?: AbortSignal,
): Promise<T> {
  const active = session.get();
  if (active && active.expiresAt <= Date.now()) {
    session.clear();
    throw new ApiError(401, fallback[401]);
  }
  let response: Response;
  try {
    response = await fetch(
      `${import.meta.env.VITE_API_BASE_URL || "/api"}${path}`,
      {
        method,
        signal,
        headers: {
          Accept: "application/json",
          ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
          ...(active ? { Authorization: `Bearer ${active.token}` } : {}),
        },
        ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
      },
    );
  } catch (error) {
    if (error instanceof Error && error.name === "AbortError") throw error;
    throw new ApiError(
      0,
      "Unable to connect. Check that the backend is running and try again.",
    );
  }
  if (!response.ok) {
    if (response.status === 401 && session.get()?.token === active?.token)
      session.clear();
    const data = (await response.json().catch(() => null)) as {
      message?: unknown;
      detail?: unknown;
      errors?: Record<string, string>;
    } | null;
    const details = data?.errors
      ? Object.values(data.errors).join(" ")
      : data?.detail || data?.message;
    throw new ApiError(
      response.status,
      typeof details === "string" && details.trim()
        ? details
        : fallback[response.status] || "The request failed. Please try again.",
    );
  }
  return response.status === 204
    ? (undefined as T)
    : ((await response.json()) as T);
}
