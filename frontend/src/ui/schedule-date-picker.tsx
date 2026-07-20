import DatePicker, { registerLocale } from "react-datepicker";
import { enUS } from "date-fns/locale/en-US";
import "react-datepicker/dist/react-datepicker.css";

// Always render the calendar in English, regardless of the viewer's OS/browser
// locale — native <input type="datetime-local"> renders its popup per-OS with
// no way for the page to override it (UI-477).
registerLocale("en-US", enUS);

/** Parses a `YYYY-MM-DDTHH:mm`-shaped local-datetime string (this form's storage format). */
function parseLocalDateTime(value: string): Date | null {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

/** Formats back to the same `YYYY-MM-DDTHH:mm` shape the form state already uses. */
function formatLocalDateTime(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function ScheduleDatePicker({
  id,
  value,
  onChange,
  placeholder,
}: {
  id?: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
}) {
  return (
    <DatePicker
      id={id}
      selected={parseLocalDateTime(value)}
      onChange={(date: Date | null) => onChange(date ? formatLocalDateTime(date) : "")}
      showTimeSelect
      timeIntervals={15}
      dateFormat="MMM d, yyyy h:mm aa"
      locale="en-US"
      placeholderText={placeholder}
      className="shell-field w-full"
      wrapperClassName="block w-full"
    />
  );
}
