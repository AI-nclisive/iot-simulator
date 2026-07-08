import { useEffect, useState } from "react";
import { apiFetch, ApiError } from "../api";
import { useShellStore } from "../shell/shell-store";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge } from "../ui/status-badge";
import { type ActivityEventDto } from "./admin-users-page";

// ── Helpers ───────────────────────────────────────────────────────────────────

function formatTime(at: string): string {
  try {
    return new Date(at).toLocaleString();
  } catch {
    return at;
  }
}

function actionLabel(event: ActivityEventDto): string {
  switch (event.action) {
    case "create":
      return `Created ${event.objectType}`;
    case "delete":
      return `Deleted ${event.objectType}`;
    case "start":
      return `Started ${event.objectType}`;
    case "stop":
      return `Stopped ${event.objectType}`;
    case "start_capture":
      return `Started capture`;
    case "change_role": {
      const role = typeof event.detail.role === "string" ? event.detail.role : "";
      return `Changed role to ${role}`;
    }
    case "change_status": {
      const status = typeof event.detail.status === "string" ? event.detail.status.toLowerCase() : "";
      return `Changed status to ${status}`;
    }
    default:
      return event.action;
  }
}

function actionTone(action: string): "accent" | "warning" | "danger" | "neutral" {
  if (action === "start" || action === "start_capture" || action === "create") return "accent";
  if (action === "stop" || action === "change_status") return "warning";
  if (action === "delete") return "danger";
  return "neutral";
}

const KNOWN_ACTIONS = [
  "create",
  "delete",
  "start",
  "stop",
  "start_capture",
  "change_role",
  "change_status",
];

const KNOWN_OBJECT_TYPES = ["data_source", "recording", "scenario", "user"];

// ── Filter bar ────────────────────────────────────────────────────────────────

function FilterSelect({
  label,
  id,
  value,
  options,
  onChange,
}: {
  label: string;
  id: string;
  value: string;
  options: { label: string; value: string }[];
  onChange: (v: string) => void;
}) {
  return (
    <label className="flex items-center gap-2 text-sm text-shell-muted" htmlFor={id}>
      {label}
      <select
        id={id}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="rounded border border-shell-line bg-white px-2 py-1 text-sm text-shell-ink focus:outline-none focus:ring-2 focus:ring-shell-accent"
        aria-label={label}
      >
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </label>
  );
}

// ── Activity timeline ─────────────────────────────────────────────────────────

function ActivityTimeline({ events }: { events: ActivityEventDto[] }) {
  if (events.length === 0) {
    return (
      <SharedStatePanel
        state="empty"
        message="No activity events match the current filters."
      />
    );
  }

  return (
    <ol className="space-y-3" aria-label="Activity timeline">
      {events.map((event) => (
        <li
          key={event.id}
          className="flex flex-col gap-1 rounded-md border border-shell-line bg-white px-4 py-3 text-sm sm:flex-row sm:items-start sm:justify-between"
        >
          <div className="min-w-0 flex items-start gap-3">
            <StatusBadge
              label={event.action.replace(/_/g, " ")}
              tone={actionTone(event.action)}
            />
            <div>
              <p className="font-medium text-shell-ink">{actionLabel(event)}</p>
              {event.objectId && (
                <p className="text-xs text-shell-muted mt-0.5">
                  {event.objectType}: <span className="font-mono">{event.objectId}</span>
                </p>
              )}
            </div>
          </div>
          <div className="shrink-0 text-right text-shell-muted">
            <p>by {event.actor}</p>
            <p className="text-xs">{formatTime(event.at)}</p>
          </div>
        </li>
      ))}
    </ol>
  );
}

// ── Page ──────────────────────────────────────────────────────────────────────

export function ActivityPage() {
  const currentProjectId = useShellStore((state) => state.currentProjectId);

  const [events, setEvents] = useState<ActivityEventDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [isLoadingMore, setIsLoadingMore] = useState(false);

  const [actorFilter, setActorFilter] = useState("all");
  const [actionFilter, setActionFilter] = useState("all");
  const [objectTypeFilter, setObjectTypeFilter] = useState("all");

  useEffect(() => {
    if (!currentProjectId) return;
    let cancelled = false;
    setIsLoading(true);
    setLoadError(null);
    setEvents([]);
    setNextCursor(null);
    apiFetch<{ events: ActivityEventDto[]; nextCursor: string | null }>(
      `/api/v1/projects/${currentProjectId}/activity?limit=50`,
    )
      .then((data) => {
        if (!cancelled) {
          setEvents(data.events);
          setNextCursor(data.nextCursor);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setLoadError(err instanceof ApiError ? err.title : "Failed to load activity.");
        }
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [currentProjectId]);

  async function loadMore() {
    if (!currentProjectId || !nextCursor || isLoadingMore) return;
    setIsLoadingMore(true);
    try {
      const data = await apiFetch<{ events: ActivityEventDto[]; nextCursor: string | null }>(
        `/api/v1/projects/${currentProjectId}/activity?limit=50&cursor=${encodeURIComponent(nextCursor)}`,
      );
      setEvents((prev) => [...prev, ...data.events]);
      setNextCursor(data.nextCursor);
    } catch {
      // silently fail — user can retry
    } finally {
      setIsLoadingMore(false);
    }
  }

  const actors = ["all", ...Array.from(new Set(events.map((e) => e.actor))).sort()];
  const actions = ["all", ...KNOWN_ACTIONS.filter((a) => events.some((e) => e.action === a))];
  const objectTypes = ["all", ...KNOWN_OBJECT_TYPES.filter((t) => events.some((e) => e.objectType === t))];

  const filtered = events.filter((e) => {
    if (actorFilter !== "all" && e.actor !== actorFilter) return false;
    if (actionFilter !== "all" && e.action !== actionFilter) return false;
    if (objectTypeFilter !== "all" && e.objectType !== objectTypeFilter) return false;
    return true;
  });

  const hasFilters = actorFilter !== "all" || actionFilter !== "all" || objectTypeFilter !== "all";

  return (
    <div className="flex h-full flex-col gap-3">
      <section className="shell-panel px-5 py-5">
        <div className="mb-5">
          <h2 className="text-2xl font-semibold text-shell-ink">Activity</h2>
          <p className="mt-2 text-sm text-shell-muted">
            User and automation actions recorded for this project. Runtime events are tracked separately in the Monitoring view.
          </p>
        </div>

        {loadError ? (
          <SharedStatePanel state="error" message={loadError} />
        ) : isLoading ? (
          <SharedStatePanel state="loading" message="Loading activity…" />
        ) : (
          <>
            <div
              className="mb-4 flex flex-wrap gap-4 items-center"
              aria-label="Activity filters"
              role="group"
            >
              <FilterSelect
                id="actor-filter"
                label="Actor"
                value={actorFilter}
                options={actors.map((a) => ({ label: a === "all" ? "All actors" : a, value: a }))}
                onChange={setActorFilter}
              />
              <FilterSelect
                id="action-filter"
                label="Action"
                value={actionFilter}
                options={actions.map((a) => ({ label: a === "all" ? "All actions" : a.replace(/_/g, " "), value: a }))}
                onChange={setActionFilter}
              />
              <FilterSelect
                id="object-type-filter"
                label="Object"
                value={objectTypeFilter}
                options={objectTypes.map((t) => ({ label: t === "all" ? "All objects" : t.replace(/_/g, " "), value: t }))}
                onChange={setObjectTypeFilter}
              />
              {hasFilters && (
                <button
                  className="text-sm text-shell-accent hover:underline"
                  onClick={() => {
                    setActorFilter("all");
                    setActionFilter("all");
                    setObjectTypeFilter("all");
                  }}
                >
                  Clear filters
                </button>
              )}
              <span className="ml-auto text-sm text-shell-muted">
                {filtered.length} event{filtered.length !== 1 ? "s" : ""}
              </span>
            </div>

            <ActivityTimeline events={filtered} />

            {nextCursor && !hasFilters && (
              <div className="mt-4 flex justify-center">
                <button
                  className="rounded border border-shell-line px-4 py-2 text-sm text-shell-ink hover:bg-shell-surface disabled:opacity-50"
                  onClick={loadMore}
                  disabled={isLoadingMore}
                >
                  {isLoadingMore ? "Loading…" : "Load more"}
                </button>
              </div>
            )}
          </>
        )}
      </section>
    </div>
  );
}
