import { useEffect, useMemo, useState } from "react";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { apiFetch } from "../api/client";
import { useShellStore } from "../shell/shell-store";
import { useLiveRuntime } from "../shell/use-live-runtime";
import type { DataSourceRow } from "../shell/data-sources-store";

export type RuntimeEventLevel = "info" | "warning" | "error";

export type RuntimeEvent = {
  id: string;
  level: RuntimeEventLevel;
  timestamp: string;
  message: string;
  category: "connection" | "runtime" | "recording" | "replay";
};

/** DTO shape returned by GET /api/v1/projects/{pid}/runtime-events */
interface RuntimeEventDto {
  id: number;
  type: string;
  at: string;
  dataSourceId: string | null;
  runId: string | null;
  payload: Record<string, unknown>;
}

interface RuntimeEventsResponse {
  events: RuntimeEventDto[];
  nextCursor: string | null;
}

function humanize(type: string): string {
  switch (type) {
    case "SOURCE_STARTED":    return "Source started";
    case "SOURCE_STOPPED":   return "Source stopped";
    case "SOURCE_STALE":     return "Source went stale";
    case "SOURCE_RECOVERED": return "Source recovered";
    case "SOURCE_ERROR":     return "Source error";
    case "HEALTH_WARNING":   return "Health warning";
    case "HEALTH_ERROR":     return "Health error";
    case "REPLAY_STARTED":   return "Replay started";
    case "REPLAY_STOPPED":   return "Replay stopped";
    case "RECORDING_STARTED": return "Recording started";
    case "RECORDING_STOPPED": return "Recording stopped";
    case "CLIENT_CONNECTED":    return "Client connected";
    case "CLIENT_DISCONNECTED": return "Client disconnected";
    default: return type.toLowerCase().replace(/_/g, " ");
  }
}

function typeToLevel(type: string): RuntimeEventLevel {
  if (type === "SOURCE_ERROR" || type === "HEALTH_ERROR") return "error";
  if (type === "SOURCE_STALE" || type === "HEALTH_WARNING") return "warning";
  return "info";
}

function typeToCategory(type: string): RuntimeEvent["category"] {
  if (["SOURCE_STARTED", "SOURCE_STOPPED", "SOURCE_STALE", "SOURCE_RECOVERED",
       "CLIENT_CONNECTED", "CLIENT_DISCONNECTED"].includes(type)) return "connection";
  if (["REPLAY_STARTED", "REPLAY_STOPPED"].includes(type)) return "replay";
  if (["RECORDING_STARTED", "RECORDING_STOPPED"].includes(type)) return "recording";
  return "runtime";
}

function mapDtoToEvent(dto: RuntimeEventDto): RuntimeEvent {
  const message =
    typeof dto.payload?.message === "string" ? dto.payload.message :
    typeof dto.payload?.detail === "string" ? dto.payload.detail :
    humanize(dto.type);
  return {
    id: String(dto.id),
    level: typeToLevel(dto.type),
    timestamp: dto.at,
    message,
    category: typeToCategory(dto.type),
  };
}

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
  const projectId = useShellStore((state) => state.currentProjectId);

  // Historical events from REST API
  const [apiEvents, setApiEvents] = useState<RuntimeEvent[]>([]);
  // Live events from SSE, prepended in front
  const [liveEventsList, setLiveEventsList] = useState<RuntimeEvent[]>([]);

  const [isLoading, setIsLoading] = useState(true);
  const [fetchError, setFetchError] = useState<string | null>(null);
  const [categoryFilter, setCategoryFilter] = useState<typeof categoryOptions[number]["value"]>("all");
  const [levelFilter, setLevelFilter] = useState<typeof levelOptions[number]["value"]>("all");
  const [expandedId, setExpandedId] = useState<RuntimeEvent["id"] | null>(null);

  // Fetch initial events from REST API
  useEffect(() => {
    if (!projectId) {
      setIsLoading(false);
      return;
    }
    let cancelled = false;
    setIsLoading(true);
    setFetchError(null);

    apiFetch<RuntimeEventsResponse>(
      `/api/v1/projects/${projectId}/runtime-events?source=${encodeURIComponent(source.id)}&limit=100`,
    )
      .then((res) => {
        if (cancelled) return;
        setApiEvents(res.events.map(mapDtoToEvent));
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        setFetchError(err instanceof Error ? err.message : "Failed to load events");
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [projectId, source.id]);

  // Subscribe to live SSE events and prepend matching ones (no duplicates)
  const { events: liveEvents } = useLiveRuntime(projectId, !!projectId);

  useEffect(() => {
    if (liveEvents.length === 0) return;
    const latest = liveEvents[0];
    if (latest.dataSourceId !== source.id) return;

    // Map SSE delta to RuntimeEvent — use at+type as stable key since SSE has no numeric id
    const mappedId = `live-${latest.at}-${latest.type}`;
    const mapped: RuntimeEvent = {
      id: mappedId,
      level: typeToLevel(latest.type),
      timestamp: latest.at,
      message: latest.detail ?? humanize(latest.type),
      category: typeToCategory(latest.type),
    };

    setLiveEventsList((prev) => {
      // Deduplicate by id
      if (prev.some((e) => e.id === mapped.id)) return prev;
      return [mapped, ...prev];
    });
  // liveEvents reference changes on each new SSE event; source.id is stable
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [liveEvents, source.id]);

  // Merge: live events (newest first) + API events, deduplicated by id
  const allEvents = useMemo(() => {
    const seen = new Set<string>();
    const merged: RuntimeEvent[] = [];
    for (const e of [...liveEventsList, ...apiEvents]) {
      if (!seen.has(e.id)) {
        seen.add(e.id);
        merged.push(e);
      }
    }
    return merged;
  }, [liveEventsList, apiEvents]);

  const visibleEvents = allEvents.filter((event) => {
    const categoryMatch = categoryFilter === "all" || event.category === categoryFilter;
    const levelMatch = levelFilter === "all" || event.level === levelFilter;
    return categoryMatch && levelMatch;
  });

  if (isLoading) {
    return (
      <SharedStatePanel
        message="Fetching runtime events from the server…"
        state="loading"
        title="Loading events"
      />
    );
  }

  if (fetchError) {
    return (
      <SharedStatePanel
        message={fetchError}
        state="error"
        title="Failed to load events"
      />
    );
  }

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
