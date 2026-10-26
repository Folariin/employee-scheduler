import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Clock3, Info, Pencil, Plus, Trash2 } from "lucide-react";
import { api } from "../api";
import { useAction } from "../hooks/queries";
import { DAYS } from "../types";
import type {
  Availability as AvailabilityRecord,
  AvailabilityRequest,
  DayOfWeek,
} from "../types";
import { validRange, timeLabel } from "../utils/dates";
import {
  Button,
  Confirm,
  ErrorNotice,
  Form,
  Loading,
  Modal,
  PageHeader,
  field,
} from "../components/ui";
const dayLabel = (day: string) => day[0] + day.slice(1).toLowerCase();
export function AvailabilityForm({
  day,
  existing,
  onClose,
  onSaved,
}: {
  day: DayOfWeek;
  existing?: AvailabilityRecord;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [validation, setValidation] = useState("");
  const action = useAction(
    (data: AvailabilityRequest) =>
      existing ? api.updateAvailability(data) : api.createAvailability(data),
    onSaved,
  );
  return (
    <Modal
      title={`${existing ? "Edit" : "Add"} ${dayLabel(day)} availability`}
      onClose={() => {
        if (!action.isPending) onClose();
      }}
    >
      <Form
        onSubmit={(data) => {
          const startTime = field(data, "startTime"),
            endTime = field(data, "endTime");
          if (!validRange(startTime, endTime)) {
            setValidation(
              "Start time must be before end time. Overnight intervals are not supported.",
            );
            return;
          }
          setValidation("");
          action.mutate({ dayOfWeek: day, startTime, endTime });
        }}
      >
        <div className="modal-body">
          <p className="muted">
            Set one continuous interval for {dayLabel(day)}. This repeats each
            week.
          </p>
          <div className="form-grid">
            <label>
              Available from
              <input
                name="startTime"
                type="time"
                step="1"
                defaultValue={existing?.startTime || "09:00"}
                required
              />
            </label>
            <label>
              Available until
              <input
                name="endTime"
                type="time"
                step="1"
                defaultValue={existing?.endTime || "17:00"}
                required
              />
            </label>
          </div>
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
            {action.isPending ? "Saving…" : "Save availability"}
          </Button>
        </div>
      </Form>
    </Modal>
  );
}
export function Availability() {
  const query = useQuery({
    queryKey: ["availability"],
    queryFn: api.availability,
  });
  const [editing, setEditing] = useState<DayOfWeek | null>(null);
  const [deleting, setDeleting] = useState<DayOfWeek | null>(null);
  const [notice, setNotice] = useState("");
  const remove = useAction(api.deleteAvailability, () => {
    setDeleting(null);
    setNotice("Recurring availability removed.");
  });
  return (
    <>
      <PageHeader
        eyebrow="A WEEK THAT WORKS FOR YOU"
        title="My Availability"
        description="Let your team know when you’re generally available to work."
      />
      <div className="notice">
        <Info size={19} />
        <span>
          This is your recurring weekly availability. Changing it does not
          automatically modify any shifts you’re already assigned to.
        </span>
      </div>
      {notice && (
        <div className="notice success" role="status">
          {notice}
        </div>
      )}
      <section className="panel availability-panel">
        <div className="panel-heading">
          <h2>
            <Clock3 size={21} />
            Weekly availability
          </h2>
          <span className="small muted">One interval per weekday</span>
        </div>
        {query.isPending ? (
          <Loading />
        ) : query.isError ? (
          <ErrorNotice error={query.error} />
        ) : (
          <div className="availability-list">
            {DAYS.map((day) => {
              const existing = query.data.find(
                (item) => item.dayOfWeek === day,
              );
              return (
                <div className="availability-row" key={day}>
                  <strong>{dayLabel(day)}</strong>
                  <span
                    className={`badge ${existing ? "published" : "unavailable"}`}
                  >
                    {existing ? "Available" : "Not set"}
                  </span>
                  <span className="availability-time">
                    {existing
                      ? `${timeLabel(existing.startTime)} – ${timeLabel(existing.endTime)}`
                      : "No availability added"}
                  </span>
                  <div className="row-actions">
                    {existing ? (
                      <>
                        <Button
                          variant="secondary"
                          aria-label={`Edit ${dayLabel(day)} availability`}
                          onClick={() => setEditing(day)}
                        >
                          <Pencil size={16} />
                          <span>Edit</span>
                        </Button>
                        <Button
                          variant="ghost"
                          aria-label={`Remove ${dayLabel(day)} availability`}
                          onClick={() => {
                            remove.reset();
                            setDeleting(day);
                          }}
                        >
                          <Trash2 size={16} />
                        </Button>
                      </>
                    ) : (
                      <Button
                        variant="secondary"
                        onClick={() => setEditing(day)}
                        aria-label={`Add ${dayLabel(day)} availability`}
                      >
                        <Plus size={16} />
                        Add availability
                      </Button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </section>
      {editing && (
        <AvailabilityForm
          day={editing}
          existing={query.data?.find((item) => item.dayOfWeek === editing)}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            setNotice("Recurring availability saved.");
          }}
        />
      )}
      {deleting && (
        <Confirm
          title={`Remove ${dayLabel(deleting)} availability?`}
          label="Remove availability"
          danger
          pending={remove.isPending}
          error={remove.error}
          onClose={() => setDeleting(null)}
          onConfirm={() => remove.mutate(deleting)}
        >
          Your recurring interval for {dayLabel(deleting)} will be removed.
          Already assigned shifts will not be changed.
        </Confirm>
      )}
    </>
  );
}
