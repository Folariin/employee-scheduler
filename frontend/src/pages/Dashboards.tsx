import { useEmployeeProfile } from "../features/auth/Auth";
import { useState } from "react";
import { useQuery, useQueries } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import {
  ArrowRight,
  CalendarDays,
  Check,
  Clock3,
  FileText,
  Plus,
  Users,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { api } from "../api";
import {
  Badge,
  Button,
  Empty,
  ErrorNotice,
  Loading,
  PageHeader,
} from "../components/ui";
import {
  WeeklyCalendar,
  ShiftList,
} from "../components/calendar/WeeklyCalendar";
import { CreateSchedule } from "./AdminSchedules";
import { addDays, hours, localDate, mondayOf, weekLabel } from "../utils/dates";
function Stat({
  icon: Icon,
  label,
  value,
  tone,
  detail,
}: {
  icon: LucideIcon;
  label: string;
  value: string | number;
  tone: string;
  detail: string;
}) {
  return (
    <article className="stat-card">
      <span className={`icon-tile ${tone}`}>
        <Icon size={24} />
      </span>
      <div>
        <strong>{value}</strong>
        <span>{label}</span>
      </div>
      <small>{detail}</small>
    </article>
  );
}
export function AdminDashboard() {
  const employees = useQuery({
    queryKey: ["employees"],
    queryFn: api.employees,
  });
  const schedules = useQuery({
    queryKey: ["schedules"],
    queryFn: api.schedules,
  });
  const [creating, setCreating] = useState(false);
  const details = useQueries({
    queries: (schedules.data || []).map((schedule) => ({
      queryKey: ["schedule", schedule.id],
      queryFn: () => api.schedule(schedule.id),
    })),
  });
  const error =
    employees.error ||
    schedules.error ||
    details.find((item) => item.error)?.error;
  const loading =
    employees.isPending ||
    schedules.isPending ||
    details.some((item) => item.isPending);
  const all = schedules.data || [];
  const upcoming = [...all]
    .filter((s) => addDays(s.weekStartDate, 6) >= localDate())
    .sort((a, b) => a.weekStartDate.localeCompare(b.weekStartDate));
  const featured = upcoming[0] || all[0];
  const featuredDetail = details.find(
    (item) => item.data?.id === featured?.id,
  )?.data;
  return (
    <>
      <PageHeader
        eyebrow="YOUR TEAM. YOUR TIME."
        title="Team overview"
        description="Review staffing, manage upcoming weeks, and keep your team informed."
      >
        <span className="today-chip">
          <CalendarDays size={16} />
          {new Date().toLocaleDateString("en-US", {
            weekday: "short",
            month: "short",
            day: "numeric",
          })}
        </span>
      </PageHeader>
      {error && <ErrorNotice error={error} />}
      <div className="stats-grid">
        <Stat
          icon={Users}
          tone="blue"
          label="Total employees"
          value={employees.isPending ? "—" : (employees.data?.length ?? "—")}
          detail="Registered team members"
        />
        <Stat
          icon={FileText}
          tone="amber"
          label="Draft schedules"
          value={
            schedules.isPending || schedules.isError
              ? "—"
              : all.filter((s) => s.status === "DRAFT").length
          }
          detail="Awaiting publication"
        />
        <Stat
          icon={Check}
          tone="green"
          label="Published schedules"
          value={
            schedules.isPending || schedules.isError
              ? "—"
              : all.filter((s) => s.status === "PUBLISHED").length
          }
          detail="Visible to assigned employees"
        />
        <Stat
          icon={CalendarDays}
          tone="violet"
          label="Scheduled shifts"
          value={
            loading || details.some((item) => item.isError)
              ? "—"
              : details.reduce(
                  (sum, item) => sum + (item.data?.shifts.length || 0),
                  0,
                )
          }
          detail="Across all weeks"
        />
      </div>
      <div className="dashboard-middle">
        <section className="panel">
          <div className="panel-heading">
            <h2>
              <CalendarDays size={19} />
              Upcoming schedules
            </h2>
            <Link className="text-link" to="/admin/schedules">
              View all
              <ArrowRight size={15} />
            </Link>
          </div>
          {schedules.isPending ? (
            <Loading />
          ) : !upcoming.length ? (
            <Empty title="Make a little room for next week">
              Create a schedule to get your team’s next week started.
            </Empty>
          ) : (
            <div className="schedule-list">
              {upcoming.slice(0, 4).map((schedule) => (
                <Link to={`/admin/schedules/${schedule.id}`} key={schedule.id}>
                  <span className="icon-tile blue compact">
                    <CalendarDays size={20} />
                  </span>
                  <span className="schedule-info">
                    <strong>{weekLabel(schedule.weekStartDate)}</strong>
                    <small>
                      {details.find((item) => item.data?.id === schedule.id)
                        ?.data?.shifts.length ?? "…"}{" "}
                      assigned shifts
                    </small>
                  </span>
                  <Badge status={schedule.status} />
                  <ArrowRight className="muted" size={16} />
                </Link>
              ))}
            </div>
          )}
        </section>
        <section className="panel">
          <div className="panel-heading">
            <h2>Workspace actions</h2>
          </div>
          <div className="quick-actions">
            <Button onClick={() => setCreating(true)}>
              <Plus size={19} />
              Create a weekly schedule
              <ArrowRight size={17} />
            </Button>
            <Link to="/admin/employees">
              <Users size={19} />
              <span>Manage your team</span>
              <ArrowRight size={17} />
            </Link>
            <Link to="/admin/schedules">
              <CalendarDays size={19} />
              <span>Explore schedules</span>
              <ArrowRight size={17} />
            </Link>
          </div>
          <div className="planning-note">
            <span>GOOD TO KNOW</span>
            <p>
              Drafts are just for you. Publish a schedule when it’s ready for
              your team to see.
            </p>
          </div>
        </section>
      </div>
      <section className="panel calendar-panel">
        <div className="panel-heading">
          <div>
            <h2>
              <CalendarDays size={20} />A week at a glance
            </h2>
            <p className="small muted">
              {featured
                ? weekLabel(featured.weekStartDate)
                : "Your next schedule will take shape here."}
            </p>
          </div>
          {featured && (
            <div className="row-actions">
              <Badge status={featured.status} />
              <Link
                className="button secondary"
                to={`/admin/schedules/${featured.id}`}
              >
                Open schedule
                <ArrowRight size={15} />
              </Link>
            </div>
          )}
        </div>
        {loading ? (
          <Loading />
        ) : featuredDetail ? (
          <WeeklyCalendar
            week={featuredDetail.weekStartDate}
            shifts={featuredDetail.shifts}
            employees={employees.data}
          />
        ) : (
          <Empty title="Every great week starts with a plan">
            Create your first weekly schedule to see it here.
          </Empty>
        )}
      </section>
      {creating && <CreateSchedule onClose={() => setCreating(false)} />}
    </>
  );
}
export function EmployeeDashboard() {
  const profile = useEmployeeProfile();
  const today = localDate();
  const week = mondayOf();
  const upcoming = useQuery({
    queryKey: ["my-shifts", "upcoming", today],
    queryFn: () => api.myShifts({ startDate: today }),
  });
  const schedules = useQuery({
    queryKey: ["my-schedules", "upcoming", week],
    queryFn: () => api.mySchedules({ startDate: week }),
  });
  const shifts = upcoming.data || [];
  const selected = schedules.data?.[0];
  const periodShifts = selected?.shifts || [];
  return (
    <>
      <PageHeader
        eyebrow="A LITTLE CLARITY FOR YOUR WEEK"
        title={profile.data?.firstName ? `Welcome back, ${profile.data.firstName}.` : "Welcome to your workspace."}
        description="Your upcoming shifts and availability, all in one place."
      >
        <Link className="button secondary" to="/employee/schedule">
          View my schedule
          <ArrowRight size={16} />
        </Link>
      </PageHeader>
      <ErrorNotice error={upcoming.error || schedules.error} />
      <div className="stats-grid">
        <Stat
          icon={CalendarDays}
          tone="blue"
          label="Upcoming shifts"
          value={upcoming.isPending || upcoming.isError ? "—" : shifts.length}
          detail="From today onward"
        />
        <Stat
          icon={Clock3}
          tone="green"
          label="Assigned hours"
          value={
            schedules.isPending || schedules.isError ? "—" : hours(periodShifts)
          }
          detail="Next assigned published week"
        />
        <Stat
          icon={Users}
          tone="violet"
          label="Scheduled days"
          value={
            schedules.isPending || schedules.isError
              ? "—"
              : new Set(periodShifts.map((s) => s.shiftDate)).size
          }
          detail="Next assigned published week"
        />
        <Stat
          icon={Check}
          tone="amber"
          label="Published weeks"
          value={schedules.isPending ? "—" : (schedules.data?.length ?? "—")}
          detail="This week and beyond"
        />
      </div>
      <div className="employee-overview">
        <section className="panel">
          <div className="panel-heading">
            <h2>Your next shift</h2>
            <span className="badge published">Published only</span>
          </div>
          {upcoming.isPending ? (
            <Loading />
          ) : shifts[0] ? (
            <>
              <ShiftList shifts={[shifts[0]]} />
              <div className="panel-padding">
                <Link
                  className="button primary full-width"
                  to="/employee/schedule"
                >
                  View my full schedule
                  <ArrowRight size={16} />
                </Link>
              </div>
            </>
          ) : (
            <Empty title="You’re all caught up">
              There are no upcoming published shifts assigned to you.
            </Empty>
          )}
          <div className="planning-note">
            <span>MAKE YOUR WEEK WORK</span>
            <p>
              Keep your recurring availability up to date to help with future
              scheduling.
            </p>
            <Link className="text-link" to="/employee/availability">
              My availability
              <ArrowRight size={15} />
            </Link>
          </div>
        </section>
        <section className="panel">
          <div className="panel-heading">
            <h2>Coming up next</h2>
            <Link className="text-link" to="/employee/schedule">
              See all
              <ArrowRight size={15} />
            </Link>
          </div>
          {upcoming.isPending ? (
            <Loading />
          ) : (
            <ShiftList shifts={shifts.slice(0, 5)} />
          )}
        </section>
      </div>
      <section className="panel">
        <div className="panel-heading">
          <h2>
            <CalendarDays size={20} />
            Your next published week
          </h2>
          {selected && (
            <span className="small muted">
              {weekLabel(selected.weekStartDate)}
            </span>
          )}
        </div>
        {schedules.isPending ? (
          <Loading />
        ) : selected ? (
          <WeeklyCalendar
            week={selected.weekStartDate}
            shifts={selected.shifts}
          />
        ) : (
          <Empty title="Nothing on the calendar yet">
            Your assigned shifts will appear when your administrator publishes a
            schedule.
          </Empty>
        )}
      </section>
    </>
  );
}
