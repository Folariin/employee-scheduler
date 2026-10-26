import { useEffect, useState } from "react";
import { NavLink, Outlet, useLocation } from "react-router-dom";
import {
  CalendarDays,
  LayoutDashboard,
  Users,
  Clock3,
  LogOut,
  Menu,
  X,
  ChevronRight,
  ShieldCheck,
} from "lucide-react";
import { session } from "../../features/auth/session";
import { useSession, useEmployeeProfile } from "../../features/auth/Auth";
import { Button } from "../ui";
export function Layout() {
  const active = useSession();
  const profile = useEmployeeProfile();
  const employeeName = [profile.data?.firstName, profile.data?.lastName].filter(Boolean).join(" ") || "Employee";
  const [open, setOpen] = useState(false);
  const location = useLocation();
  useEffect(() => {
    if (!open) return;
    const previous = document.activeElement as HTMLElement | null;
    const panel = document.getElementById("workspace-navigation");
    panel?.querySelector<HTMLElement>("a")?.focus();
    const handleKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
      if (event.key === "Tab") {
        const controls = panel?.querySelectorAll<HTMLElement>("a, button");
        const first = controls?.[0],
          last = controls?.[controls.length - 1];
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault();
          last?.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault();
          first?.focus();
        }
      }
    };
    document.addEventListener("keydown", handleKey);
    return () => {
      document.removeEventListener("keydown", handleKey);
      previous?.focus();
    };
  }, [open]);
  if (!active) return null;
  const admin = active.role === "ADMIN";
  const links = admin
    ? [
        { to: "/admin/dashboard", label: "Dashboard", icon: LayoutDashboard },
        { to: "/admin/employees", label: "Employees", icon: Users },
        { to: "/admin/schedules", label: "Schedules", icon: CalendarDays },
      ]
    : [
        {
          to: "/employee/dashboard",
          label: "My Dashboard",
          icon: LayoutDashboard,
        },
        { to: "/employee/schedule", label: "My Schedule", icon: CalendarDays },
        {
          to: "/employee/availability",
          label: "My Availability",
          icon: Clock3,
        },
      ];
  const title =
    links.find((link) => location.pathname.startsWith(link.to))?.label ||
    "Workspace";
  return (
    <div className="app-shell">
      <Button
        className="mobile-menu"
        variant="secondary"
        aria-label="Open navigation"
        aria-expanded={open}
        aria-controls="workspace-navigation"
        onClick={() => setOpen(true)}
      >
        <Menu size={22} />
      </Button>
      {open && (
        <button
          className="sidebar-scrim"
          aria-label="Close navigation"
          onClick={() => setOpen(false)}
        />
      )}
      <aside
        id="workspace-navigation"
        className={`sidebar ${open ? "open" : ""}`}
        role={open ? "dialog" : undefined}
        aria-modal={open ? true : undefined}
        aria-label={open ? "Workspace navigation" : undefined}
      >
        <div className="sidebar-top">
          <NavLink
            className="brand"
            to={admin ? "/admin/dashboard" : "/employee/dashboard"}
            onClick={() => setOpen(false)}
          >
            <span className="brand-icon">
              <CalendarDays size={25} />
            </span>
            <span>
              Employee<span className="brand-second">Scheduler</span>
            </span>
          </NavLink>
          <button
            className="close-nav"
            aria-label="Close navigation"
            onClick={() => setOpen(false)}
          >
            <X size={21} />
          </button>
        </div>
        <div className="workspace-tag">
          {admin ? "TEAM WORKSPACE" : "YOUR WORKSPACE"}
        </div>
        <nav aria-label="Main navigation">
          {links.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              onClick={() => setOpen(false)}
              className={({ isActive }) =>
                isActive ? "nav-link active" : "nav-link"
              }
            >
              <Icon size={19} />
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="sidebar-tip">
            <ShieldCheck size={19} />
            <span>
              {admin
                ? "A little planning goes a long way."
                : "Your next week, all in one place."}
            </span>
          </div>
          <button className="logout" onClick={() => session.clear()}>
            <LogOut size={18} /> Sign out
          </button>
          <div className="sidebar-user">
            <span className="avatar">{admin ? "AD" : "ME"}</span>
            <span>
              <strong>{admin ? "Administrator" : employeeName}</strong>
              <small>{admin ? "Administrator" : "Employee"}</small>
            </span>
          </div>
        </div>
      </aside>
      <div className="main-shell">
        <header className="topbar">
          <div className="breadcrumb">
            Workspace
            <ChevronRight size={13} />
            <span>{title}</span>
          </div>
          <div className="account">
            <span className="status-dot" />
            Team workspace
            <span className="divider" />
            <span className="avatar small-avatar">{admin ? "AD" : "ME"}</span>
            <span>
              <strong>{admin ? "Admin workspace" : employeeName}</strong>
              <small>{admin ? "Administrator" : "Employee"}</small>
            </span>
          </div>
        </header>
        <main id="main-content" className="main-content">
          <Outlet />
        </main>
        <footer className="app-footer">
          <span>Employee Scheduler</span>
          <span>A better week starts here.</span>
        </footer>
      </div>
    </div>
  );
}
