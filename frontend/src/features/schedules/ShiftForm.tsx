import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api } from "../../api";
import { useAction } from "../../hooks/queries";
import { addDays, fullName, validRange } from "../../utils/dates";
import {
  Button,
  ErrorNotice,
  Form,
  Loading,
  Modal,
  field,
} from "../../components/ui";
import type { Shift, ShiftRequest } from "../../types";
export function ShiftForm({
  scheduleId,
  week,
  shift,
  onClose,
  onSaved,
}: {
  scheduleId: string;
  week: string;
  shift?: Shift;
  onClose: () => void;
  onSaved: () => void;
}) {
  const employees = useQuery({
    queryKey: ["employees"],
    queryFn: api.employees,
  });
  const [validation, setValidation] = useState("");
  const action = useAction(
    (data: ShiftRequest) =>
      shift
        ? api.updateShift(shift.id, data)
        : api.createShift(scheduleId, data),
    onSaved,
  );
  const submit = (data: FormData) => {
    const request = {
      employeeId: field(data, "employeeId"),
      shiftDate: field(data, "shiftDate"),
      startTime: field(data, "startTime"),
      endTime: field(data, "endTime"),
    };
    if (!request.employeeId) {
      setValidation("Choose an employee.");
      return;
    }
    if (request.shiftDate < week || request.shiftDate > addDays(week, 6)) {
      setValidation("Choose a date within this schedule week.");
      return;
    }
    if (!validRange(request.startTime, request.endTime)) {
      setValidation(
        "Start time must be before end time. Overnight shifts are not supported.",
      );
      return;
    }
    setValidation("");
    action.mutate(request);
  };
  return (
    <Modal
      title={shift ? "Edit assigned shift" : "Assign a shift"}
      onClose={() => {
        if (!action.isPending) onClose();
      }}
    >
      <Form onSubmit={submit}>
        <div className="modal-body">
          <p className="muted">
            Plan a little time for the people who make it happen.
          </p>
          {employees.isPending ? (
            <Loading label="Loading employees…" />
          ) : employees.isError ? (
            <ErrorNotice error={employees.error} />
          ) : (
            <label>
              Employee
              <select
                name="employeeId"
                defaultValue={shift?.employeeId || ""}
                required
              >
                <option value="" disabled>
                  Select a team member
                </option>
                {employees.data.map((employee) => (
                  <option key={employee.id} value={employee.id}>
                    {fullName(employee)} · {employee.employeeNumber}
                  </option>
                ))}
              </select>
            </label>
          )}
          <label>
            Shift date
            <input
              name="shiftDate"
              type="date"
              defaultValue={shift?.shiftDate || week}
              min={week}
              max={addDays(week, 6)}
              required
            />
          </label>
          <div className="form-grid">
            <label>
              Start time
              <input
                name="startTime"
                type="time"
                step="1"
                defaultValue={shift?.startTime || "09:00"}
                required
              />
            </label>
            <label>
              End time
              <input
                name="endTime"
                type="time"
                step="1"
                defaultValue={shift?.endTime || "17:00"}
                required
              />
            </label>
          </div>
          <p className="small muted">
            Shifts must be within the employee’s recurring availability and
            cannot overlap an existing assignment.
          </p>
          <ErrorNotice error={validation || action.error} />
        </div>
        <div className="modal-footer">
          <Button
            variant="secondary"
            type="button"
            disabled={action.isPending}
            onClick={onClose}
          >
            Cancel
          </Button>
          <Button
            type="submit"
            disabled={
              action.isPending ||
              employees.isPending ||
              employees.isError ||
              !employees.data?.length
            }
          >
            {action.isPending
              ? "Saving…"
              : shift
                ? "Save shift"
                : "Assign shift"}
          </Button>
        </div>
      </Form>
    </Modal>
  );
}
