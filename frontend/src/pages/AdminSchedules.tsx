import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  ArrowLeft,
  ArrowUpRight,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  LockKeyhole,
  Plus,
  Send,
  Trash2,
} from "lucide-react";
import { api } from "../api";
import { useAction } from "../hooks/queries";
import { GenerateSchedule } from "../features/schedules/GenerateSchedule";
import { ShiftForm } from "../features/schedules/ShiftForm";
import { WeeklyCalendar } from "../components/calendar/WeeklyCalendar";
import {
  Badge,
  Button,
  Confirm,
  Empty,
  ErrorNotice,
  Form,
  Loading,
  Modal,
  PageHeader,
  field,
} from "../components/ui";
import {
  fullName,
  mondayOf,
  weekLabel,
  dateLabel,
  timeLabel,
} from "../utils/dates";
import type { Shift } from "../types";
export function CreateSchedule({ onClose }: { onClose: () => void }) {
  const navigate = useNavigate();
  const [validation, setValidation] = useState("");
  const action = useAction(api.createSchedule, (result) => {
    onClose();
    navigate(`/admin/schedules/${result.id}`);
  });
  return (
    <Modal
      title="Plan a new week"
      onClose={() => {
        if (!action.isPending) onClose();
      }}
    >
      <Form
        onSubmit={(data) => {
          const date = field(data, "weekStartDate");
          if (date !== mondayOf(date)) {
            setValidation("Schedules must start on a Monday.");
            return;
          }
          setValidation("");
          action.mutate(date);
        }}
      >
        <div className="modal-body">
          <p className="muted">
            Start with a draft. Add your team’s shifts, then publish when you’re
            ready.
          </p>
          <label>
            Week starting Monday
            <input
              name="weekStartDate"
              type="date"
              min="0001-01-01"
              max="9999-12-31"
              defaultValue={mondayOf()}
              required
            />
          </label>
          <p className="small muted">
            One schedule per week. Existing weeks cannot be created again.
          </p>
          <ErrorNotice error={validation || action.error} />
        </div>
        <div className="modal-footer">
          <Button
            type="button"
            variant="secondary"
            disabled={action.isPending}
            onClick={onClose}
          >
            Cancel
          </Button>
          <Button type="submit" disabled={action.isPending}>
            {action.isPending ? "Creating…" : "Create schedule"}
          </Button>
        </div>
      </Form>
    </Modal>
  );
}
export function AdminSchedules() {
  const query = useQuery({ queryKey: ["schedules"], queryFn: api.schedules });
  const [creating, setCreating] = useState(false);
  const [filter, setFilter] = useState("ALL");
  const schedules = (query.data || []).filter(
    (schedule) => filter === "ALL" || schedule.status === filter,
  );
  return (
    <>
      <PageHeader
        eyebrow="MAKE ROOM FOR A GREAT WEEK"
        title="Schedules"
        description="A clear plan for your people, one week at a time."
      >
        <Button onClick={() => setCreating(true)}>
          <Plus size={18} />
          Create schedule
        </Button>
      </PageHeader>
      <section className="panel">
        <div className="panel-heading">
          <h2>
            <CalendarDays size={20} />
            Weekly schedules
          </h2>
          <div className="tabs" aria-label="Filter schedules">
            {["ALL", "DRAFT", "PUBLISHED"].map((status) => (
              <button
                key={status}
                aria-pressed={filter === status}
                className={filter === status ? "selected" : ""}
                onClick={() => setFilter(status)}
              >
                {status === "ALL"
                  ? "All schedules"
                  : status === "DRAFT"
                    ? "Drafts"
                    : "Published"}
              </button>
            ))}
          </div>
        </div>
        {query.isPending ? (
          <Loading />
        ) : query.isError ? (
          <ErrorNotice error={query.error} />
        ) : !schedules.length ? (
          <Empty title="Your next week is a blank canvas">
            {filter === "ALL"
              ? "Create a weekly schedule to start assigning shifts."
              : "No schedules match this status."}
          </Empty>
        ) : (
          <div className="schedule-grid">
            {schedules.map((schedule) => (
              <Link
                className="schedule-tile"
                to={`/admin/schedules/${schedule.id}`}
                key={schedule.id}
              >
                <div>
                  <span className="icon-tile blue">
                    <CalendarDays size={23} />
                  </span>
                  <Badge status={schedule.status} />
                </div>
                <h3>{weekLabel(schedule.weekStartDate)}</h3>
                <p>
                  {schedule.status === "DRAFT"
                    ? "Ready for your planning"
                    : "Published and shared with your team"}
                </p>
                <span className="text-link">
                  View schedule
                  <ArrowUpRight size={16} />
                </span>
              </Link>
            ))}
          </div>
        )}
      </section>
      {creating && <CreateSchedule onClose={() => setCreating(false)} />}
    </>
  );
}
export function ScheduleDetail() {
  const { id = "" } = useParams();
  const query = useQuery({
    queryKey: ["schedule", id],
    queryFn: () => api.schedule(id),
  });
  const employees = useQuery({
    queryKey: ["employees"],
    queryFn: api.employees,
  });
  const schedules = useQuery({
    queryKey: ["schedules"],
    queryFn: api.schedules,
  });
  const [form, setForm] = useState<Shift | "new" | null>(null);
  const [generating, setGenerating] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [deleting, setDeleting] = useState<Shift | null>(null);
  const [notice, setNotice] = useState("");
  const publish = useAction(api.publish, () => {
    setPublishing(false);
    setNotice(
      "Schedule published. Your team can now see their assigned shifts.",
    );
  });
  const remove = useAction(api.deleteShift, () => {
    setDeleting(null);
    setNotice("Shift deleted.");
  });
  if (query.isPending) return <Loading />;
  if (query.isError)
    return (
      <>
        <Link className="back-link" to="/admin/schedules">
          <ArrowLeft size={16} />
          All schedules
        </Link>
        <ErrorNotice error={query.error} />
      </>
    );
  const schedule = query.data;
  const editable = schedule.status === "DRAFT";
  const ordered = [...(schedules.data || [])].sort((a, b) =>
    a.weekStartDate.localeCompare(b.weekStartDate),
  );
  const position = ordered.findIndex((item) => item.id === id);
  return (
    <>
      <Link className="back-link" to="/admin/schedules">
        <ArrowLeft size={16} />
        All schedules
      </Link>
      <PageHeader
        eyebrow="WEEKLY PLANNING"
        title={weekLabel(schedule.weekStartDate)}
        description={`${schedule.shifts.length} assigned shifts · ${editable ? "Build a week that works for everyone." : "Published and visible to your team."}`}
      >
        <Badge status={schedule.status} />
        {editable && (
          <>
            <Button variant="secondary" onClick={() => setGenerating(true)}>Generate schedule</Button>
            <Button variant="secondary" onClick={() => setForm("new")}>
              <Plus size={17} />
              Assign shift
            </Button>
            <Button
              onClick={() => {
                publish.reset();
                setPublishing(true);
              }}
            >
              <Send size={17} />
              Publish schedule
            </Button>
          </>
        )}
      </PageHeader>
      {notice && (
        <div className="notice success" role="status">
          {notice}
        </div>
      )}
      {!editable && (
        <div className="notice">
          <LockKeyhole size={18} />
          This schedule is published and read-only. Your employees can see their
          assigned shifts.
        </div>
      )}
      <ErrorNotice error={employees.error} />
      <section className="panel calendar-panel">
        <div className="panel-heading">
          <h2>
            <CalendarDays size={20} />
            Weekly calendar
          </h2>
          <div className="button-group">
            {position > 0 && (
              <Link
                className="button secondary"
                aria-label="Previous schedule"
                to={`/admin/schedules/${ordered[position - 1].id}`}
              >
                <ChevronLeft size={18} />
              </Link>
            )}
            {position >= 0 && position < ordered.length - 1 && (
              <Link
                className="button secondary"
                aria-label="Next schedule"
                to={`/admin/schedules/${ordered[position + 1].id}`}
              >
                <ChevronRight size={18} />
              </Link>
            )}
          </div>
        </div>
        <WeeklyCalendar
          week={schedule.weekStartDate}
          shifts={schedule.shifts}
          employees={employees.data}
          editable={editable}
          onSelect={(shift) => setForm(shift)}
        />
      </section>
      {editable && schedule.shifts.length > 0 && (
        <section className="panel">
          <div className="panel-heading">
            <h2>Assigned shifts</h2>
            <span className="muted small">Select a calendar card to edit</span>
          </div>
          <div className="assignment-list">
            {schedule.shifts.map((shift) => (
              <div key={shift.id}>
                <span>
                  <strong>
                    {employees.data?.find((e) => e.id === shift.employeeId)
                      ? fullName(
                          employees.data.find(
                            (e) => e.id === shift.employeeId,
                          )!,
                        )
                      : "Employee"}
                  </strong>
                  <small>
                    {dateLabel(shift.shiftDate, {
                      weekday: "short",
                      month: "short",
                      day: "numeric",
                    })}{" "}
                    · {timeLabel(shift.startTime)} – {timeLabel(shift.endTime)}
                  </small>
                </span>
                <div className="row-actions">
                  <Button variant="secondary" onClick={() => setForm(shift)}>
                    Edit
                  </Button>
                  <Button
                    variant="ghost"
                    aria-label={`Delete shift on ${shift.shiftDate}`}
                    onClick={() => {
                      remove.reset();
                      setDeleting(shift);
                    }}
                  >
                    <Trash2 size={17} />
                  </Button>
                </div>
              </div>
            ))}
          </div>
        </section>
      )}
      {generating && editable && <GenerateSchedule key={id} scheduleId={id} week={schedule.weekStartDate}
        onClose={() => setGenerating(false)} onSaved={() => { setGenerating(false); setNotice("Generated assignments applied to draft. Review the calendar before publishing."); }} />}
      {form && editable && (
        <ShiftForm
          key={form === "new" ? "new" : form.id}
          scheduleId={id}
          week={schedule.weekStartDate}
          shift={form === "new" ? undefined : form}
          onClose={() => setForm(null)}
          onSaved={() => {
            setForm(null);
            setNotice("Shift saved.");
          }}
        />
      )}
      {publishing && editable && (
        <Confirm
          title="Ready to publish this week?"
          label="Publish schedule"
          pending={publish.isPending}
          error={publish.error}
          onClose={() => setPublishing(false)}
          onConfirm={() => publish.mutate(id)}
        >
          Employees will be able to see their assigned shifts. This schedule
          will become read-only, and no further shift changes will be permitted.
          This cannot be undone.
        </Confirm>
      )}
      {deleting && editable && (
        <Confirm
          title="Delete this shift?"
          label="Delete shift"
          danger
          pending={remove.isPending}
          error={remove.error}
          onClose={() => setDeleting(null)}
          onConfirm={() => remove.mutate(deleting.id)}
        >
          Remove the assignment on {dateLabel(deleting.shiftDate)}? The employee
          account and schedule will remain.
        </Confirm>
      )}
    </>
  );
}
