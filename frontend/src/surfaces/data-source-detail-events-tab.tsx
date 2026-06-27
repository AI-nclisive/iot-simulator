import { useState } from "react";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { getEventsForSource, type RuntimeEventLevel } from "./mock-source-events";
import type { DataSourceRow } from "./mock-data-sources";

function levelIcon(level: RuntimeEventLevel) {
  if (level === "error") return "●";
  if (level === "warning") return "◆";
  return "·";
}

function levelClass(level: RuntimeEventLevel) {
  if (level === "error") return "text-shell-danger";
  if (level === "warning") return "text-amber-600";
  return "text-shell-muted";
}

const categoryOptions = [
  { label: "All categories", value: "all" },
  { label: "Connection", value: "connection" },
  { label: "Runtime", value: "runtime" },
  { label: "Recording", value: "recording" },
  { label: "Replay", value: "replay" },
] as const;

const levelOptions = [
  { label: "All levels", value: "all" },
  { label: "Info", value: "info" },
  { label: "Warning", value: "warning" },
  { label: "Error", value: "error" },
] as const;

export function DataSourceDetailEventsTab({ source }: { source: DataSourceRow }) {
  const allEvents = getEventsForSource(source.id);
  const [categoryFilter, setCategoryFilter] = useState<typeof categoryOptions[number]["value"]>("all");
  const [levelFilter, setLevelFilter] = useState<typeof levelOptions[number]["value"]>("all");

  const visibleEvents = allEvents.filter((event) => {
    const categoryMatch = categoryFilter === "all" || event.category === categoryFilter;
    const levelMatch = levelFilter === "all" || event.level === levelFilter;
    return categoryMatch && levelMatch;
  });

  if (allEvents.length === 0) {
    return (
      <SharedStatePanel
        message="Runtime events will appear here once this source has been active. Events are separate from user activity history."
        state="empty"
        title="No runtime events recorded yet."
      />
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <label className="flex items-center gap-2 text-sm text-shell-muted">
          Category
          <select
            className="shell-field py-1.5"
            value={categoryFilter}
            onChange={(e) => setCategoryFilter(e.target.value as typeof categoryOptions[number]["value"])}
          >
            {categoryOptions.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </label>

        <label className="flex items-center gap-2 text-sm text-shell-muted">
          Level
          <select
            className="shell-field py-1.5"
            value={levelFilter}
            onChange={(e) => setLevelFilter(e.target.value as typeof levelOptions[number]["value"])}
          >
            {levelOptions.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </label>

        <span className="text-sm text-shell-muted">
          {visibleEvents.length} of {allEvents.length} event{allEvents.length === 1 ? "" : "s"}
        </span>
      </div>

      {visibleEvents.length === 0 ? (
        <SharedStatePanel
          message="No events match the current filters. Try clearing one of the active filters."
          state="empty"
          title="No matching events."
        />
      ) : (
        <div className="overflow-hidden rounded-md border border-shell-line bg-white">
          <ul className="divide-y divide-shell-line">
            {visibleEvents.map((event) => (
              <li key={event.id} className="flex items-start gap-3 px-4 py-3">
                <span
                  className={`mt-0.5 shrink-0 font-mono text-base leading-none ${levelClass(event.level)}`}
                  aria-hidden="true"
                >
                  {levelIcon(event.level)}
                </span>
                <div className="min-w-0 flex-1">
                  <p className="text-sm text-shell-ink">{event.message}</p>
                  <p className="mt-1 text-xs text-shell-muted">
                    {event.timestamp} · {event.category}
                  </p>
                </div>
                <span
                  className={`shrink-0 text-xs font-medium uppercase tracking-wide ${levelClass(event.level)}`}
                >
                  {event.level}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      <p className="text-xs text-shell-muted">
        Runtime events reflect source and process activity. User actions are tracked separately in the activity log.
      </p>
    </div>
  );
}
