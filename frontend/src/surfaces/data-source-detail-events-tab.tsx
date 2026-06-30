import { useState } from "react";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { getEventsForSource, type RuntimeEvent, type RuntimeEventLevel } from "./mock-source-events";
import type { DataSourceRow } from "../shell/data-sources-store";

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
  const [expandedId, setExpandedId] = useState<RuntimeEvent["id"] | null>(null);

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
            {visibleEvents.map((event) => {
              const isExpanded = expandedId === event.id;
              const detailId = `event-detail-${event.id}`;
              return (
                <li key={event.id}>
                  <button
                    className="flex w-full items-start gap-3 px-4 py-3 text-left hover:bg-shell-base/40 transition"
                    type="button"
                    aria-expanded={isExpanded}
                    aria-controls={detailId}
                    onClick={() => setExpandedId(isExpanded ? null : event.id)}
                  >
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
                  </button>
                  {isExpanded ? (
                    <dl
                      id={detailId}
                      className="mx-4 mb-3 grid grid-cols-2 gap-2 rounded-md border border-shell-line bg-shell-base/30 px-3 py-2 text-xs"
                    >
                      <div>
                        <dt className="font-semibold uppercase tracking-wide text-shell-muted">Timestamp</dt>
                        <dd className="mt-0.5 font-mono text-shell-ink">{event.timestamp}</dd>
                      </div>
                      <div>
                        <dt className="font-semibold uppercase tracking-wide text-shell-muted">Category</dt>
                        <dd className="mt-0.5 text-shell-ink capitalize">{event.category}</dd>
                      </div>
                      <div>
                        <dt className="font-semibold uppercase tracking-wide text-shell-muted">Level</dt>
                        <dd className={`mt-0.5 font-medium uppercase ${levelClass(event.level)}`}>{event.level}</dd>
                      </div>
                      <div>
                        <dt className="font-semibold uppercase tracking-wide text-shell-muted">Event ID</dt>
                        <dd className="mt-0.5 font-mono text-shell-muted">{event.id}</dd>
                      </div>
                    </dl>
                  ) : null}
                </li>
              );
            })}
          </ul>
        </div>
      )}

      <p className="text-xs text-shell-muted">
        Runtime events reflect source and process activity. User actions are tracked separately in the activity log.
      </p>
    </div>
  );
}
