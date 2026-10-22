import { useEffect, useState, useSyncExternalStore } from "react";
import { Navigate, Outlet, useLocation, useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { ArrowRight, CalendarDays, ShieldCheck } from "lucide-react";
import { session } from "./session";
import { api } from "../../api";
import { ApiError } from "../../api/client";
import { Button, ErrorNotice, Form, field } from "../../components/ui";
import type { Role } from "../../types";
export const useSession = () =>
  useSyncExternalStore(session.subscribe, session.get);
export const homeFor = (role: Role) =>
  role === "ADMIN" ? "/admin/dashboard" : "/employee/dashboard";
export function SessionLifecycle() {
  const active = useSession();
  const client = useQueryClient();
  useEffect(() => session.subscribe(() => client.clear()), [client]);
  useEffect(() => {
    if (!active) return;
    let timer: ReturnType<typeof setTimeout>;
    const check = () => {
      clearTimeout(timer);
      if (session.get()?.token !== active.token) return;
      const remaining = active.expiresAt - Date.now();
      if (remaining <= 0) session.clear();
      else timer = setTimeout(check, Math.min(remaining, 2147483647));
    };
    check();
    window.addEventListener("focus", check);
    return () => {
      clearTimeout(timer);
      window.removeEventListener("focus", check);
    };
  }, [active]);
  return null;
}
export function Protected({ role }: { role: Role }) {
  const active = useSession();
  const location = useLocation();
  if (!active)
    return (
      <Navigate
        to="/login"
        replace
        state={{ expired: location.pathname !== "/" }}
      />
    );
  return active.role === role ? (
    <Outlet />
  ) : (
    <Navigate to={homeFor(active.role)} replace />
  );
}
export function Landing() {
  const active = useSession();
  return <Navigate to={active ? homeFor(active.role) : "/login"} replace />;
}
export function Login() {
  const active = useSession();
  const navigate = useNavigate();
  const location = useLocation();
  const [error, setError] = useState<unknown>();
  const [pending, setPending] = useState(false);
  if (active) return <Navigate to={homeFor(active.role)} replace />;
  const login = async (data: FormData) => {
    setPending(true);
    setError(undefined);
    try {
      const response = await api.login(
        field(data, "email"),
        String(data.get("password") || ""),
      );
      session.set(response);
      navigate(homeFor(response.role), { replace: true });
    } catch (error) {
      setError(
        error instanceof ApiError && error.status === 401
          ? new Error("Email or password is incorrect. Please try again.")
          : error,
      );
    } finally {
      data.delete("password");
      setPending(false);
    }
  };
  return (
    <main id="main-content" className="login-page">
      <section className="login-story">
        <a className="brand" href="/login">
          <span className="brand-icon">
            <CalendarDays size={27} />
          </span>
          <span>
            Employee<span className="brand-second">Scheduler</span>
          </span>
        </a>
        <div>
          <span className="story-label">A LITTLE MORE ORGANIZED.</span>
          <h1>
            Great teams.
            <br />
            Better weeks.
          </h1>
          <p>
            Bring your people and their schedules together.
            <br />
            Less back and forth. More time for what matters.
          </p>
          <div className="story-calendar" aria-hidden="true">
            <div className="story-calendar-head">
              <span>This week</span>
              <span>● &nbsp; All set</span>
            </div>
            <div className="story-days">
              {"MTWTF".split("").map((d, i) => (
                <span key={i}>{d}</span>
              ))}
            </div>
            <div className="story-blocks">
              <i />
              <i />
              <i />
              <i />
              <i />
            </div>
          </div>
        </div>
        <small>One workspace. A week that works.</small>
      </section>
      <section className="login-form">
        <div className="login-box">
          <span className="login-mark">
            <CalendarDays size={27} />
          </span>
          <p className="eyebrow">WELCOME TO YOUR WORKSPACE</p>
          <h2>Good to see you.</h2>
          <p>Sign in to plan your team’s next great week.</p>
          {location.state?.expired && (
            <div className="notice">
              Please sign in to continue. Sessions are kept only while this tab
              is open.
            </div>
          )}
          <Form onSubmit={(data) => void login(data)}>
            <label>
              Email address
              <input
                name="email"
                type="email"
                autoComplete="username"
                placeholder="you@company.com"
                required
                maxLength={254}
              />
            </label>
            <label>
              Password
              <input
                name="password"
                type="password"
                autoComplete="current-password"
                placeholder="Enter your password"
                required
              />
            </label>
            <ErrorNotice error={error} />
            <Button type="submit" disabled={pending}>
              {pending ? "Signing in…" : "Sign in to workspace"}
              <ArrowRight size={18} />
            </Button>
          </Form>
          <div className="login-security">
            <ShieldCheck size={16} /> Secure access for your team
          </div>
          <p className="small">Need an account? Contact your administrator.</p>
        </div>
      </section>
    </main>
  );
}
