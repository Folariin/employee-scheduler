import { Sparkles } from "lucide-react";
import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { api } from "../../api";
import { WeeklyCalendar } from "../../components/calendar/WeeklyCalendar";
import { useAction } from "../../hooks/queries";
import { addDays, dateLabel, timeLabel, validRange } from "../../utils/dates";
import { Button, ErrorNotice, Empty, Loading, Modal } from "../../components/ui";
import type { GenerationRequest, StaffingRequirement, Shift } from "../../types";

export function GenerateSchedule({ scheduleId, week, onClose, onSaved, existingShifts = [], existingEmployeeNames = {} }: {
  scheduleId: string; week: string; onClose: () => void; onSaved: () => void;
  existingShifts?: Shift[]; existingEmployeeNames?: Record<string, string>;
}) {
  const [requirements, setRequirements] = useState<(StaffingRequirement & { key: number })[]>([
    { key: 0, date: week, startTime: "09:00", endTime: "17:00", requiredEmployees: 1 },
  ]);
  const [source, setSource] = useState<"manual" | "ai">("manual");
  const [instructions, setInstructions] = useState("");
  const [reviewed, setReviewed] = useState(false);
  const interpretation = useMutation({
    mutationFn: () => api.interpretInstructions(scheduleId, instructions),
    onSuccess: data => {
      setReviewed(false); setValidation("");
      setRequirements(data.requirements.map((row, index) => ({ ...row, key: index })));
      setNextKey(data.requirements.length);
      // These are visible planning defaults, never attributed to the model.
      setMaxHours(data.maxWeeklyHours ?? 40);
      setFair(data.balanceHoursFairly ?? true);
    },
  });
  const [nextKey, setNextKey] = useState(1);
  const [maxHours, setMaxHours] = useState(40);
  const [fair, setFair] = useState(true);
  const [minShiftMinutes, setMinShiftMinutes] = useState(120);
  const [maxShiftMinutes, setMaxShiftMinutes] = useState(480);
  const flexible = requirements.some(r => r.mode === "FLEXIBLE");
  const [phase, setPhase] = useState<"requirements" | "preview" | "confirm">("requirements");
  const [selected, setSelected] = useState<string[]>([]);
  const [validation, setValidation] = useState("");
  const preview = useMutation({
    mutationFn: (data: GenerationRequest) => api.generatePreview(scheduleId, data),
    onSuccess: (data) => { setSelected(data.assignments.map(a => a.suggestionId)); setPhase("preview"); },
  });
  const apply = useAction((suggestionIds: string[]) => api.applyGenerated(scheduleId, {
    previewToken: preview.data!.previewToken, suggestionIds,
  }), onSaved);
  const pending = preview.isPending || apply.isPending || interpretation.isPending;
  const edit = () => { setPhase("requirements"); setValidation(""); apply.reset(); preview.reset(); };
  const generate = () => {
    setValidation("");
    if (!requirements.length || (source === "ai" && (!interpretation.data || !reviewed))) {
      setValidation("Interpret instructions, resolve questions in the editable requirements, and confirm your review first."); return;
    }
    if (!Number.isInteger(maxHours) || maxHours < 1 || maxHours > 168 || requirements.some(r =>
      r.date < week || r.date > addDays(week, 6) || !validRange(r.startTime, r.endTime) ||
      !Number.isInteger(r.requiredEmployees) || r.requiredEmployees < 1 || r.requiredEmployees > 50)) {
      setValidation("Use dates in this week, start before end, 1–50 employees and a weekly limit of 1–168 hours."); return;
    }
    if (flexible && (!Number.isInteger(minShiftMinutes) || !Number.isInteger(maxShiftMinutes) ||
      minShiftMinutes < 1 || maxShiftMinutes > 1440 || minShiftMinutes > maxShiftMinutes)) {
      setValidation("Flexible shift durations must satisfy 1 ≤ minimum ≤ maximum ≤ 1440 minutes."); return;
    }
    for (let i = 0; i < requirements.length; i++) for (let j = i + 1; j < requirements.length; j++) {
      const a = requirements[i], b = requirements[j];
      if (a.date === b.date && a.startTime < b.endTime && b.startTime < a.endTime) {
        setValidation("Requirements on the same day must not overlap. Adjacent intervals are allowed."); return;
      }
    }
    if (requirements.reduce((sum, r) => sum + r.requiredEmployees, 0) > 1000) {
      setValidation("A preview supports at most 1000 requested assignments."); return;
    }
    preview.mutate({ requirements: requirements.map(({ date, startTime, endTime, requiredEmployees, mode }) =>
      ({ date, startTime, endTime, requiredEmployees, ...(mode === "FLEXIBLE" ? { mode } : {}) })),
      maxWeeklyHours: maxHours, balanceHoursFairly: fair,
      ...(flexible ? { minShiftMinutes, maxShiftMinutes } : {}),
    });
  };
  const change = (key: number, patch: Partial<StaffingRequirement>) =>
    { setReviewed(false); setRequirements(rows => rows.map(row => row.key === key ? { ...row, ...patch } : row)); };
  const data = preview.data;
  return <Modal wide title="Generate schedule" onClose={() => { if (!pending) onClose(); }}>
    <div className="modal-body generation-panel">
      <ol className="flow-steps" aria-label="Schedule generation progress">
        <li aria-current={phase === "requirements" ? "step" : undefined}><span>01</span> Staffing requirements</li>
        <li aria-current={phase === "preview" ? "step" : undefined}><span>02</span> Review preview</li>
        <li aria-current={phase === "confirm" ? "step" : undefined}><span>03</span> Confirm assignments</li>
      </ol>
      <p className="muted">Plan staffing for this draft week. Previewing saves no shifts; applying keeps existing assignments and does not publish.</p>
      {phase === "requirements" ? <form id="generation-form" onSubmit={event => { event.preventDefault(); generate(); }}>
        <fieldset disabled={pending}>
          <label>Requirement entry<select value={source} onChange={e => { setSource(e.target.value as "manual" | "ai"); setReviewed(false); }}>
            <option value="manual">Manual entry</option><option value="ai">AI instructions</option>
          </select></label>
          {source === "ai" && <section className="ai-workspace" aria-label="AI staffing interpretation">
            <div className="ai-heading"><Sparkles size={22} aria-hidden="true" /><div><h3>Describe your staffing plan</h3><p className="small muted">Turn your instructions into requirements you can review and edit.</p></div></div>
            <p className="notice">AI drafts requirements only. Instructions are sent to Groq. Do not include employee names, credentials or sensitive information.</p>
            <label>Scheduling instructions<textarea aria-describedby="instructions-help" placeholder="For example: Two employees working the full 9 AM to 5 PM shift on Tuesday." maxLength={4000} value={instructions} onChange={e => {
              setInstructions(e.target.value); setReviewed(false); interpretation.reset();
            }} /></label>
            <div id="instructions-help" className="input-help"><span>Include days, times, employee counts, and full or flexible shifts.</span><span>{instructions.length.toLocaleString()} / 4,000</span></div>
            <Button type="button" disabled={!instructions.trim() || pending} onClick={() => { setReviewed(false); interpretation.mutate(); }}>Interpret instructions</Button>
            {interpretation.isPending && <Loading label="Interpreting instructions…" />}
            <ErrorNotice error={interpretation.error} />
            {interpretation.data && <div className="ai-result" aria-live="polite">
              <h3>Review AI interpretation</h3><p>{interpretation.data.summary}</p>
              {interpretation.data.clarificationQuestions.length > 0 && <section><h4>Needs your clarification</h4><ul>{interpretation.data.clarificationQuestions.map((q, i) => <li key={i}>{q}</li>)}</ul></section>}
              {interpretation.data.unsupportedConstraints.length > 0 && <section className="notice error"><h4>Outside the scheduling rules</h4><ul>{interpretation.data.unsupportedConstraints.map((q, i) => <li key={i}>{q}</li>)}</ul></section>}
              <p className="small muted">Resolve questions by refining and interpreting again, or manually adding/editing the requirements below. Unsupported constraints will not be enforced.</p>
              <p className="small muted">{interpretation.data.maxWeeklyHours === null ? "No weekly hours cap was specified. The form uses a 40-hour planning default; review it." : "Weekly hours cap was explicitly requested."} {interpretation.data.balanceHoursFairly === null ? "Fairness was not specified. Balancing is a form default; review it." : "Fairness preference was explicitly requested."}</p>
              <label className="generation-check"><input type="checkbox" checked={reviewed} onChange={e => setReviewed(e.target.checked)} />I reviewed the requirements and planning defaults, resolved questions, and accept the listed limitations.</label>
            </div>}
          </section>}

          {requirements.map((row, index) => <section className="generation-requirement" key={row.key}>
            <h3>Requirement {index + 1}</h3>
            <label>Scheduling mode {index + 1}<select value={row.mode || "FIXED"} onChange={e => change(row.key, { mode: e.target.value as "FIXED" | "FLEXIBLE" })}>
              <option value="FIXED">Fixed shifts — exact times</option>
              <option value="FLEXIBLE">Flexible shifts — staffing window</option>
            </select></label>
            {row.mode === "FLEXIBLE" ? <p className="notice">Request distinct employees working sometime in this window. This does not guarantee simultaneous coverage.</p> :
              <p className="small muted">Employees must cover the full exact interval.</p>}
            <div className="form-grid">
              <label>Date {index + 1}<input type="date" min={week} max={addDays(week, 6)} value={row.date} required onChange={e => change(row.key, { date: e.target.value })} /></label>
              <label>{row.mode === "FLEXIBLE" ? "Distinct employees" : "Employees needed"} {index + 1}<input type="number" min="1" max="50" value={row.requiredEmployees} required onChange={e => change(row.key, { requiredEmployees: Number(e.target.value) })} /></label>
              <label>{row.mode === "FLEXIBLE" ? "Window start" : "Start time"} {index + 1}<input type="time" step="1" value={row.startTime} required onChange={e => change(row.key, { startTime: e.target.value })} /></label>
              <label>{row.mode === "FLEXIBLE" ? "Window end" : "End time"} {index + 1}<input type="time" step="1" value={row.endTime} required onChange={e => change(row.key, { endTime: e.target.value })} /></label>
            </div>
            <Button type="button" variant="ghost" disabled={requirements.length === 1} onClick={() => { setReviewed(false); setRequirements(rows => rows.filter(r => r.key !== row.key)); }}>Remove requirement {index + 1}</Button>
          </section>)}
          <Button type="button" variant="secondary" disabled={requirements.length >= 100} onClick={() => {
            setReviewed(false); setRequirements(rows => [...rows, { key: nextKey, date: week, startTime: "09:00", endTime: "17:00", requiredEmployees: 1 }]); setNextKey(k => k + 1);
          }}>Add requirement</Button>
          {flexible && <>
            <div className="form-grid">
              <label>Minimum flexible shift minutes<input type="number" min="1" max="1440" value={minShiftMinutes} required onChange={e => { setReviewed(false); setMinShiftMinutes(Number(e.target.value)); }} /></label>
              <label>Maximum flexible shift minutes<input type="number" min="1" max="1440" value={maxShiftMinutes} required onChange={e => { setReviewed(false); setMaxShiftMinutes(Number(e.target.value)); }} /></label>
            </div>
            <p className="small muted">New flexible shifts must meet these lengths within availability, the window and remaining weekly hours. Short fragments cause shortages. Defaults are planning preferences, not legal or break rules.</p>
          </>}
          <label>Maximum weekly hours per employee<input type="number" min="1" max="168" value={maxHours} required onChange={e => { setReviewed(false); setMaxHours(Number(e.target.value)); }} /></label>
          <label className="generation-check"><input type="checkbox" checked={fair} onChange={e => { setReviewed(false); setFair(e.target.checked); }} />Balance hours fairly</label>
        </fieldset>
      </form> : data && <>
        <h3>{phase === "confirm" ? "Confirm selected assignments" : "Review suggestions"}</h3>
        <p className="small muted">Preview expires at {new Date(data.expiresAt).toLocaleTimeString()}. Changing availability or assignments requires a new preview.</p>
        {data.coverage.map(c => <div key={c.requirementIndex} className={`notice ${c.shortage ? "error" : "success"}`}>
          <span>{c.mode === "FLEXIBLE" ? "Flexible · distinct employees" : "Fixed shifts"} · {dateLabel(c.date)} · {timeLabel(c.startTime)}–{timeLabel(c.endTime)}: {c.requiredEmployees} required, {c.existingEmployees} existing, {c.suggestedEmployees} suggested.
            {c.mode === "FLEXIBLE" && <small> {c.explanation}</small>}
            {c.shortage > 0 && <strong> Shortage: {c.shortage}. {c.mode !== "FLEXIBLE" && c.explanation}</strong>}</span>
        </div>)}
        {!data.assignments.length ? <Empty title="No new assignments suggested">Targets may already be covered, or no employees meet the availability, conflict and hours constraints. Review the coverage above.</Empty> : <div className="generation-suggestions">
          {data.assignments.map(a => <label className="generation-check" key={a.suggestionId}>
            <input type="checkbox" disabled={pending || phase === "confirm"} checked={selected.includes(a.suggestionId)} onChange={e => setSelected(ids => e.target.checked ? [...ids, a.suggestionId] : ids.filter(id => id !== a.suggestionId))} />
            <span>{a.displayName} · {dateLabel(a.shiftDate)} · {timeLabel(a.startTime)}–{timeLabel(a.endTime)}</span>
          </label>)}
        </div>}
        {data.assignments.length > 0 && <section className="generation-calendar" aria-label="Selected shift preview">
          <h3>Selected suggested shifts</h3>
          <p className="small muted">Dashed cards are selected suggestions and are not saved; existing shifts remain. {data.coverage.some(c => c.mode === "FLEXIBLE") ? "Flexible counts distinct employees, not simultaneous coverage." : "Fixed suggestions cover the full exact interval."}</p>
          <WeeklyCalendar week={week} preview suggestedShiftIds={selected}
            shifts={[...existingShifts, ...data.assignments.filter(a => selected.includes(a.suggestionId)).map(a => ({ ...a, id: a.suggestionId, scheduleId }))]}
            employeeNames={{ ...existingEmployeeNames, ...Object.fromEntries(data.assignments.map(a => [a.employeeId, a.displayName])) }} />
        </section>}
        {data.workloads.length > 0 && <div className="table-scroll"><table><caption>Weekly hours for the complete preview</caption><thead><tr><th>Employee</th><th>Existing</th><th>Suggested</th><th>Total</th></tr></thead><tbody>
          {data.workloads.map(w => <tr key={w.employeeId}><td>{w.displayName}</td><td>{w.existingWeeklyHours}</td><td>{w.proposedWeeklyHours}</td><td>{w.resultingWeeklyHours}</td></tr>)}
        </tbody></table></div>}
        {data.workloads.some(w => w.existingWeeklyHours > maxHours) && <div className="notice error">Existing assignments already exceed the requested weekly limit for one or more employees. They are preserved; no additional hours are proposed for those employees.</div>}
        <p>{selected.length} selected. Deselected suggestions will not be applied and may leave additional staffing gaps.</p>
        {phase === "confirm" && <p>Apply these {selected.length} assignments to the draft? Existing shifts stay in place. The schedule will remain unpublished.</p>}
      </>}
      {preview.isPending && <Loading label="Finding available employees…" />}
      <ErrorNotice error={validation || preview.error || apply.error} />
    </div>
    <div className="modal-footer">
      <Button variant="secondary" disabled={pending} onClick={onClose}>Cancel</Button>
      {phase === "confirm" && <Button variant="secondary" disabled={pending} onClick={() => setPhase("preview")}>Back to suggestions</Button>}
      {phase !== "requirements" && <Button variant="secondary" disabled={pending} onClick={edit}>Edit requirements</Button>}
      {phase === "requirements" ? <Button type="submit" form="generation-form" disabled={pending || !requirements.length || (source === "ai" && (!interpretation.data || !reviewed))}>{preview.isPending ? "Generating…" : "Generate preview"}</Button> :
        phase === "preview" ? <Button disabled={!selected.length || pending} onClick={() => setPhase("confirm")}>Review selection</Button> :
        <Button disabled={!selected.length || pending} onClick={() => {
          if (Date.now() >= Date.parse(data!.expiresAt)) { setValidation("Preview expired. Edit requirements and generate a new preview."); return; }
          apply.mutate(selected);
        }}>{apply.isPending ? "Applying…" : "Apply to draft"}</Button>}
    </div>
  </Modal>;
}
