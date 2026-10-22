import { CalendarDays, ChevronLeft, ChevronRight } from "lucide-react";
import type { Employee, Shift } from "../../types";
import {
  addDays,
  dateLabel,
  fullName,
  localDate,
  minutes,
  timeLabel,
  weekLabel,
  mondayOf,
} from "../../utils/dates";
import { Button, Empty } from "../ui";
export function WeekNav({
  week,
  onChange,
}: {
  week: string;
  onChange: (week: string) => void;
}) {
  return (
    <div className="week-nav">
      <div className="button-group">
        <Button variant="secondary" onClick={() => onChange(mondayOf())}>
          Today
        </Button>
        <Button
          variant="secondary"
          aria-label="Previous week"
          onClick={() => onChange(addDays(week, -7))}
        >
          <ChevronLeft size={18} />
        </Button>
        <Button
          variant="secondary"
          aria-label="Next week"
          onClick={() => onChange(addDays(week, 7))}
        >
          <ChevronRight size={18} />
        </Button>
      </div>
      <strong>{weekLabel(week)}</strong>
      <span className="view-label">Week view</span>
    </div>
  );
}
export function WeeklyCalendar({
  week,
  shifts,
  employees = [],
  editable = false,
  onSelect,
}: {
  week: string;
  shifts: Shift[];
  employees?: Employee[];
  editable?: boolean;
  onSelect?: (shift: Shift) => void;
}) {
  const days = Array.from({ length: 7 }, (_, i) => addDays(week, i));
  const startHour = Math.min(
    8,
    ...shifts.map((s) => Math.floor(minutes(s.startTime) / 60)),
  );
  const endHour = Math.max(
    20,
    ...shifts.map((s) => Math.ceil(minutes(s.endTime) / 60)),
  );
  const height = (endHour - startHour) * 50;
  const name = (shift: Shift) => {
    const employee = employees.find((e) => e.id === shift.employeeId);
    return employee
      ? fullName(employee)
      : employees.length
        ? "Employee unavailable"
        : "Your shift";
  };
  const card = (shift: Shift, index: number, style?: React.CSSProperties) => {
    const tone = employees.length
      ? Math.max(
          0,
          employees.findIndex((employee) => employee.id === shift.employeeId),
        )
      : index + Math.max(0, days.indexOf(shift.shiftDate));
    const content = (
      <>
        <strong>{name(shift)}</strong>
        <span>
          {timeLabel(shift.startTime)} – {timeLabel(shift.endTime)}
        </span>
        <small>{editable ? "Click to edit" : "Assigned shift"}</small>
      </>
    );
    return editable ? (
      <button
        key={shift.id}
        className={`shift-card color-${tone % 5}`}
        style={style}
        onClick={() => onSelect?.(shift)}
        aria-label={`Edit ${name(shift)}, ${dateLabel(shift.shiftDate)}, ${timeLabel(shift.startTime)}`}
      >
        {content}
      </button>
    ) : (
      <div
        key={shift.id}
        className={`shift-card color-${tone % 5}`}
        style={style}
      >
        {content}
      </div>
    );
  };
  return (
    <>
      {!shifts.length && (
        <div className="calendar-empty">
          <CalendarDays size={17} /> No shifts assigned this week.
        </div>
      )}
      <div
        className="calendar-desktop"
        role="region"
        aria-label={`Weekly calendar ${weekLabel(week)}`}
        tabIndex={0}
      >
        <div className="calendar-grid">
          <div className="calendar-corner" />
          {days.map((day) => (
            <div
              key={day}
              className={`day-heading ${day === localDate() ? "today" : ""}`}
            >
              <strong>{dateLabel(day, { weekday: "short" })}</strong>
              <span>{dateLabel(day)}</span>
            </div>
          ))}
          <div className="time-axis" style={{ height }}>
            {Array.from({ length: endHour - startHour + 1 }, (_, i) => (
              <span key={i} style={{ top: i * 50 }}>
                {timeLabel(`${String(startHour + i).padStart(2, "0")}:00`)}
              </span>
            ))}
          </div>
          {days.map((day) => {
            const items = shifts
              .filter((s) => s.shiftDate === day)
              .sort((a, b) => a.startTime.localeCompare(b.startTime));
            const lanes: Shift[][] = [];
            const positions = new Map<string, number>();
            for (const item of items) {
              let lane = lanes.findIndex(
                (group) =>
                  minutes(group[group.length - 1].endTime) <=
                  minutes(item.startTime),
              );
              if (lane < 0) {
                lane = lanes.length;
                lanes.push([]);
              }
              lanes[lane].push(item);
              positions.set(item.id, lane);
            }
            return (
              <div className="day-column" key={day} style={{ height }}>
                {items.map((shift, i) =>
                  card(shift, i, {
                    top:
                      ((minutes(shift.startTime) - startHour * 60) / 60) * 50 +
                      3,
                    height: Math.max(
                      34,
                      ((minutes(shift.endTime) - minutes(shift.startTime)) /
                        60) *
                        50 -
                        6,
                    ),
                    left: `calc(${((positions.get(shift.id) || 0) / lanes.length) * 100}% + 4px)`,
                    width: `calc(${100 / lanes.length}% - 8px)`,
                  }),
                )}
              </div>
            );
          })}
        </div>
      </div>
      <div className="calendar-mobile">
        {days.map((day) => (
          <section className="agenda-day" key={day}>
            <h3>
              {dateLabel(day, {
                weekday: "long",
                month: "short",
                day: "numeric",
              })}
            </h3>
            {shifts
              .filter((s) => s.shiftDate === day)
              .sort((a, b) => a.startTime.localeCompare(b.startTime))
              .map((s, i) => card(s, i))}
            {!shifts.some((s) => s.shiftDate === day) && (
              <p className="muted small">No shifts</p>
            )}
          </section>
        ))}
      </div>
    </>
  );
}
export function ShiftList({ shifts }: { shifts: Shift[] }) {
  return shifts.length ? (
    <div className="shift-list">
      {shifts.map((shift) => (
        <div key={shift.id} className="shift-list-item">
          <span className="date-tile">
            <small>{dateLabel(shift.shiftDate, { weekday: "short" })}</small>
            <strong>{dateLabel(shift.shiftDate)}</strong>
          </span>
          <div>
            <strong>Assigned shift</strong>
            <p>
              {timeLabel(shift.startTime)} – {timeLabel(shift.endTime)}
            </p>
          </div>
          <span className="badge published">Published</span>
        </div>
      ))}
    </div>
  ) : (
    <Empty title="A little breathing room">
      Your published shifts will appear here when assigned.
    </Empty>
  );
}
