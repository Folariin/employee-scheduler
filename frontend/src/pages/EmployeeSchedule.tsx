import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { CalendarDays, List } from "lucide-react";
import { api } from "../api";
import { addDays, localDate, mondayOf } from "../utils/dates";
import { PageHeader, Loading, ErrorNotice } from "../components/ui";
import {
  WeeklyCalendar,
  WeekNav,
  ShiftList,
} from "../components/calendar/WeeklyCalendar";
export function EmployeeSchedule() {
  const [week, setWeek] = useState(mondayOf());
  const [filter, setFilter] = useState("UPCOMING");
  const shifts = useQuery({
    queryKey: ["my-shifts", week],
    queryFn: () => api.myShifts({ startDate: week, endDate: addDays(week, 6) }),
  });
  const schedules = useQuery({
    queryKey: ["my-schedules", "all"],
    queryFn: () => api.mySchedules(),
  });
  const all = (schedules.data || [])
    .flatMap((schedule) => schedule.shifts)
    .sort(
      (a, b) =>
        a.shiftDate.localeCompare(b.shiftDate) ||
        a.startTime.localeCompare(b.startTime),
    );
  const visible = all.filter(
    (shift) =>
      filter === "ALL" ||
      (filter === "UPCOMING"
        ? shift.shiftDate >= localDate()
        : shift.shiftDate < localDate()),
  );
  return (
    <>
      <PageHeader
        eyebrow="YOUR WEEK, AT A GLANCE"
        title="My Schedule"
        description="Your assigned shifts from published schedules. A plan you can count on."
      >
        <span className="badge published">Published shifts only</span>
      </PageHeader>
      <section className="panel calendar-panel">
        <div className="panel-heading">
          <h2>
            <CalendarDays size={20} />
            Weekly calendar
          </h2>
          <span className="small muted">Your time, clearly laid out</span>
        </div>
        <WeekNav week={week} onChange={setWeek} />
        {shifts.isPending ? (
          <Loading label="Loading your week…" />
        ) : shifts.isError ? (
          <ErrorNotice error={shifts.error} />
        ) : (
          <WeeklyCalendar week={week} shifts={shifts.data} />
        )}
      </section>
      <section className="panel">
        <div className="panel-heading">
          <h2>
            <List size={20} />
            Your assigned shifts
          </h2>
          <div className="tabs" aria-label="Filter assigned shifts">
            {["UPCOMING", "PAST", "ALL"].map((value) => (
              <button
                key={value}
                aria-pressed={filter === value}
                className={filter === value ? "selected" : ""}
                onClick={() => setFilter(value)}
              >
                {value === "UPCOMING"
                  ? "Upcoming"
                  : value === "PAST"
                    ? "Past"
                    : "All"}
              </button>
            ))}
          </div>
        </div>
        {schedules.isPending ? (
          <Loading />
        ) : schedules.isError ? (
          <ErrorNotice error={schedules.error} />
        ) : (
          <ShiftList shifts={visible} />
        )}
      </section>
    </>
  );
}
