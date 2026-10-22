export const localDate = (date = new Date()) =>
  `${String(date.getFullYear()).padStart(4, "0")}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
export const parseDate = (value: string) => new Date(`${value}T12:00:00`);
export const addDays = (value: string, amount: number) => {
  const date = parseDate(value);
  date.setDate(date.getDate() + amount);
  return localDate(date);
};
export const mondayOf = (value = localDate()) => {
  const date = parseDate(value);
  return addDays(value, -((date.getDay() + 6) % 7));
};
export const dateLabel = (
  value: string,
  options: Intl.DateTimeFormatOptions = { month: "short", day: "numeric" },
) => parseDate(value).toLocaleDateString("en-US", options);
export const weekLabel = (monday: string) =>
  `${dateLabel(monday)} – ${dateLabel(addDays(monday, 6), { month: "short", day: "numeric", year: "numeric" })}`;
export const timeLabel = (value: string) =>
  new Date(`2000-01-01T${value}`).toLocaleTimeString("en-US", {
    hour: "numeric",
    minute: "2-digit",
  });
export const minutes = (value: string) => {
  const [h, m, s = "0"] = value.split(":");
  return Number(h) * 60 + Number(m) + Number(s) / 60;
};
export const hours = (shifts: { startTime: string; endTime: string }[]) =>
  Math.round(
    shifts.reduce(
      (total, s) => total + (minutes(s.endTime) - minutes(s.startTime)) / 60,
      0,
    ) * 100,
  ) / 100;
export const validRange = (start: string, end: string) =>
  !!start && !!end && minutes(start) < minutes(end);
export const fullName = (person: { firstName: string; lastName: string }) =>
  `${person.firstName} ${person.lastName}`;
