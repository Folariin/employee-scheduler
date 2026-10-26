import { DAYS } from "../types";
const dayLabel = (day: string) => day[0] + day.slice(1).toLowerCase();
import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Plus,
  Search,
  Users,
  Pencil,
  Trash2,
  ArrowUpRight,
} from "lucide-react";
import { api } from "../api";
import { useAction } from "../hooks/queries";
import { timeLabel, fullName, dateLabel } from "../utils/dates";
import {
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
import type { Employee, UpdateEmployee } from "../types";
function EmployeeForm({
  employee,
  onClose,
  onSaved,
}: {
  employee?: Employee;
  onClose: () => void;
  onSaved: () => void;
}) {
  const client = useQueryClient();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>();
  const submit = async (data: FormData) => {
    setPending(true);
    setError(undefined);
    const details: UpdateEmployee = {
      firstName: field(data, "firstName"),
      lastName: field(data, "lastName"),
      phoneNumber: field(data, "phoneNumber") || null,
      hireDate: field(data, "hireDate"),
    };
    try {
      if (employee) await api.updateEmployee(employee.id, details);
      else
        await api.createEmployee({
          ...details,
          email: field(data, "email"),
          employeeNumber: field(data, "employeeNumber"),
          temporaryPassword: String(data.get("temporaryPassword") || ""),
        });
      await client.invalidateQueries();
      onSaved();
    } catch (error) {
      setError(error);
    } finally {
      data.delete("temporaryPassword");
      setPending(false);
    }
  };
  return (
    <Modal
      title={employee ? "Edit employee" : "Add a team member"}
      onClose={() => {
        if (!pending) onClose();
      }}
    >
      <Form onSubmit={(data) => void submit(data)}>
        <div className="modal-body form-grid">
          <label>
            First name
            <input
              name="firstName"
              defaultValue={employee?.firstName}
              required
              maxLength={255}
            />
          </label>
          <label>
            Last name
            <input
              name="lastName"
              defaultValue={employee?.lastName}
              required
              maxLength={255}
            />
          </label>
          <label>
            Email address
            <input
              name="email"
              type="email"
              autoComplete="off"
              defaultValue={employee?.email}
              disabled={!!employee}
              required
              maxLength={254}
            />
          </label>
          <label>
            Employee number
            <input
              name="employeeNumber"
              defaultValue={employee?.employeeNumber}
              disabled={!!employee}
              required
              maxLength={255}
            />
          </label>
          <label>
            Phone number <span className="optional">optional</span>
            <input
              name="phoneNumber"
              type="tel"
              defaultValue={employee?.phoneNumber || ""}
              maxLength={255}
            />
          </label>
          <label>
            Hire date
            <input
              name="hireDate"
              type="date"
              min="0001-01-01"
              max="9999-12-31"
              defaultValue={employee?.hireDate}
              required
            />
          </label>
          {!employee && (
            <label className="full-width">
              Temporary password
              <input
                name="temporaryPassword"
                type="password"
                autoComplete="new-password"
                required
                maxLength={72}
              />
              <small>
                Share securely with the employee before saving. It cannot be
                retrieved afterward.
              </small>
            </label>
          )}
          {employee && (
            <p className="small muted full-width">
              Email and employee number cannot be changed through the current
              API.
            </p>
          )}
          <div className="full-width">
            <ErrorNotice error={error} />
          </div>
        </div>
        <div className="modal-footer">
          <Button
            type="button"
            variant="secondary"
            disabled={pending}
            onClick={onClose}
          >
            Cancel
          </Button>
          <Button type="submit" disabled={pending}>
            {pending
              ? "Saving…"
              : employee
                ? "Save changes"
                : "Create employee"}
          </Button>
        </div>
      </Form>
    </Modal>
  );
}
function EmployeeDetails({
  id,
  onClose,
  onEdit,
}: {
  id: string;
  onClose: () => void;
  onEdit: (employee: Employee) => void;
}) {
  const query = useQuery({
    queryKey: ["employee", id],
    queryFn: () => api.employee(id),
  });
  return (
    <Modal title="Employee details" onClose={onClose}>
      <div className="modal-body">
        {query.isPending ? (
          <Loading />
        ) : query.isError ? (
          <ErrorNotice error={query.error} />
        ) : (
          <>
            <div className="person-heading">
              <span className="avatar large-avatar">
                {query.data.firstName[0]}
                {query.data.lastName[0]}
              </span>
              <div>
                <h3>{fullName(query.data)}</h3>
                <p>{query.data.employeeNumber}</p>
              </div>
            </div>
            <dl className="detail-list">
              <div>
                <dt>Email</dt>
                <dd>{query.data.email}</dd>
              </div>
              <div>
                <dt>Phone</dt>
                <dd>{query.data.phoneNumber || "Not provided"}</dd>
              </div>
              <div>
                <dt>Hire date</dt>
                <dd>
                  {dateLabel(query.data.hireDate, {
                    month: "long",
                    day: "numeric",
                    year: "numeric",
                  })}
                </dd>
              </div>
            </dl>
          </>
        )}
      </div>
      <div className="modal-footer">
        <Button variant="secondary" onClick={onClose}>
          Close
        </Button>
        {query.data && (
          <Button onClick={() => onEdit(query.data)}>
            <Pencil size={16} />
            Edit employee
          </Button>
        )}
      </div>
    </Modal>
  );
}
function EmployeeAvailability({ employee, onClose }: { employee: Employee; onClose: () => void }) {
  const query = useQuery({ queryKey: ["employee-availability", employee.id], queryFn: () => api.employeeAvailability(employee.id) });
  return <Modal title={`${fullName(employee)} — weekly availability`} onClose={onClose}>
    <div className="modal-body"><p className="small muted">Read-only recurring availability. Existing assignments are not changed.</p>
      {query.isPending ? <Loading /> : query.isError ? <ErrorNotice error={query.error} /> : <>
        {!query.data.length && <p className="notice">No recurring availability has been set.</p>}
        <dl className="detail-list">{DAYS.map(day => {
          const row = query.data.find(item => item.dayOfWeek === day);
          return <div key={day}><dt>{dayLabel(day)}</dt><dd>{row ? `${timeLabel(row.startTime)} – ${timeLabel(row.endTime)}` : "Unavailable — no interval set"}</dd></div>;
        })}</dl>
      </>}
    </div><div className="modal-footer"><Button variant="secondary" onClick={onClose}>Close</Button></div>
  </Modal>;
}
export function Employees() {
  const query = useQuery({ queryKey: ["employees"], queryFn: api.employees });
  const [availabilityEmployee, setAvailabilityEmployee] = useState<Employee | null>(null);
  const [search, setSearch] = useState("");
  const [modal, setModal] = useState<"create" | Employee | null>(null);
  const [detail, setDetail] = useState<string | null>(null);
  const [deleting, setDeleting] = useState<Employee | null>(null);
  const [notice, setNotice] = useState("");
  const remove = useAction(
    (id: string) => api.deleteEmployee(id),
    () => {
      setDeleting(null);
      setNotice("Employee and associated user account deleted.");
    },
  );
  const results = (query.data || []).filter((employee) =>
    `${fullName(employee)} ${employee.email} ${employee.employeeNumber}`
      .toLowerCase()
      .includes(search.toLowerCase()),
  );
  return (
    <>
      <PageHeader
        eyebrow="PEOPLE & TEAMS"
        title="Employees"
        description="Manage employee accounts and contact details in one place."
      >
        <Button onClick={() => setModal("create")}>
          <Plus size={18} />
          Add employee
        </Button>
      </PageHeader>
      {notice && (
        <div className="notice success" role="status">
          {notice}
        </div>
      )}
      <section className="panel">
        <div className="panel-heading">
          <h2>
            <Users size={20} />
            Team directory{" "}
            <span className="count">{query.data?.length || 0}</span>
          </h2>
          <label className="search-field">
            <Search size={17} />
            <input
              aria-label="Search employees"
              placeholder="Search name, email, or number…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </label>
        </div>
        {query.isPending ? (
          <Loading />
        ) : query.isError ? (
          <ErrorNotice error={query.error} />
        ) : !results.length ? (
          <Empty
            title={
              search ? "No matching team members" : "Your team starts here"
            }
          >
            {search
              ? "Try another name, email, or number."
              : "Add your first employee to start building a schedule."}
          </Empty>
        ) : (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Team member</th>
                  <th>Employee number</th>
                  <th>Hire date</th>
                  <th>Phone</th>
                  <th>
                    <span className="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {results.map((employee, i) => (
                  <tr key={employee.id}>
                    <td>
                      <button
                        className="person-link"
                        onClick={() => setDetail(employee.id)}
                      >
                        <span className={`avatar color-${i % 5}`}>
                          {employee.firstName[0]}
                          {employee.lastName[0]}
                        </span>
                        <span>
                          <strong>{fullName(employee)}</strong>
                          <small>{employee.email}</small>
                        </span>
                      </button>
                    </td>
                    <td>
                      <span className="employee-number">
                        {employee.employeeNumber}
                      </span>
                    </td>
                    <td>
                      {dateLabel(employee.hireDate, {
                        month: "short",
                        day: "numeric",
                        year: "numeric",
                      })}
                    </td>
                    <td>{employee.phoneNumber || "—"}</td>
                    <td>
                      <div className="row-actions">
                        <Button variant="secondary" aria-label={`View ${fullName(employee)} availability`} onClick={() => setAvailabilityEmployee(employee)}>Availability</Button>
                        <Button
                          variant="ghost"
                          aria-label={`View ${fullName(employee)}`}
                          onClick={() => setDetail(employee.id)}
                        >
                          <ArrowUpRight size={17} />
                        </Button>
                        <Button
                          variant="ghost"
                          aria-label={`Edit ${fullName(employee)}`}
                          onClick={() => setModal(employee)}
                        >
                          <Pencil size={16} />
                        </Button>
                        <Button
                          variant="ghost"
                          aria-label={`Delete ${fullName(employee)}`}
                          onClick={() => {
                            remove.reset();
                            setDeleting(employee);
                          }}
                        >
                          <Trash2 size={16} />
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
      {availabilityEmployee && <EmployeeAvailability employee={availabilityEmployee} onClose={() => setAvailabilityEmployee(null)} />}
      {modal && (
        <EmployeeForm
          employee={modal === "create" ? undefined : modal}
          onClose={() => setModal(null)}
          onSaved={() => {
            setNotice(
              modal === "create"
                ? "Employee account created."
                : "Employee updated.",
            );
            setModal(null);
          }}
        />
      )}
      {detail && (
        <EmployeeDetails
          id={detail}
          onClose={() => setDetail(null)}
          onEdit={(employee) => {
            setDetail(null);
            setModal(employee);
          }}
        />
      )}
      {deleting && (
        <Confirm
          title="Delete this employee?"
          label="Delete employee"
          danger
          pending={remove.isPending}
          error={remove.error}
          onClose={() => setDeleting(null)}
          onConfirm={() => remove.mutate(deleting.id)}
        >
          This permanently deletes {fullName(deleting)} and their user account.
          Records referenced by schedules may prevent deletion.
        </Confirm>
      )}
    </>
  );
}
