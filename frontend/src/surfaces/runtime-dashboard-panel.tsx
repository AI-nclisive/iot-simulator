import { Link } from "react-router-dom";
import { useShellStore } from "../shell/shell-store";
import { useActiveRuns, type ActiveRunResponse } from "../shell/use-active-runs";
import { useLiveRuntime } from "../shell/use-live-runtime";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StaleBanner } from "../ui/stale-banner";
import { StatusBadge, type StatusTone } from "../ui/status-badge";

type RunState = ActiveRunResponse["runState"];

function processTone(processType: "Recording" | "Replay" | "Scenario"): StatusTone {
  if (processType === "Recording") {
    return "warning";
  }

  if (processType === "Scenario") {
    return "neutral";
  }

  return "accent";
}

function runStateTone(state: RunState): StatusTone {
  if (state === "failed") return "danger";
  if (state === "running") return "warning";
  if (state === "queued") return "neutral";
  if (state === "stopped") return "neutral";
  if (state === "completed") return "neutral";
  return "accent";
}

function runProcessLabel(
  state: RunState,
  processType?: "Recording" | "Replay" | "Scenario",
): string {
  const proc = processType ?? "Run";
  if (state === "queued") return `${proc} waiting`;
  if (state === "running") return `${proc} in progress`;
  if (state === "failed") return `${proc} failed`;
  if (state === "completed") return `${proc} completed`;
  return `${proc} stopped`;
}

function RunSkeleton() {
  return (
    <div className="space-y-3" aria-busy="true" aria-label="Loading active runs">
      {[0, 1, 2].map((i) => (
        <div
          key={i}
          className="animate-pulse rounded-md border border-shell-line bg-white px-4 py-4"
        >
          <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
            <div className="min-w-0 flex-1 space-y-2">
              <div className="h-4 w-40 rounded bg-shell-line" />
              <div className="h-3 w-28 rounded bg-shell-line/60" />
            </div>
            <div className="flex gap-2">
              <div className="h-6 w-24 rounded-full bg-shell-line" />
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}

export function RuntimeDashboardPanel() {
  const projectId = useShellStore((state) => state.currentProjectId);
  // Live runtime stream drives the connection/stale indicator and per-source
  // health summary. The active-run process list (recordings/replays/scenarios)
  // is polled separately via useActiveRuns (UI-111).
  const { sources, status: liveStatus } = useLiveRuntime(projectId, !!projectId);
  const { runs, isLoading, error } = useActiveRuns(projectId);

  const isStale = liveStatus === "stale" || liveStatus === "reconnecting";
  const unhealthy = sources.filter((s) => s.health === "Error" || s.health === "Warning");

  return (
    <section aria-label="Runtime dashboard" className="shell-panel px-5 py-5">
      {isStale ? (
        <StaleBanner
          message={
            liveStatus === "reconnecting"
              ? "Reconnecting to live runtime updates…"
              : "Live runtime updates have paused. Showing the last known state."
          }
        />
      ) : null}

      {sources.length > 0 ? (
        <div className="mb-4 flex flex-wrap items-center gap-2">
          <StatusBadge label={`${sources.length} sources`} tone="neutral" />
          {unhealthy.length > 0 ? (
            <StatusBadge
              label={`${unhealthy.length} need attention`}
              tone="warning"
            />
          ) : (
            <StatusBadge label="All healthy" tone="accent" />
          )}
        </div>
      ) : null}

      {error ? (
        <div className="mb-4 rounded-md border border-shell-danger/30 bg-shell-danger/10 px-3 py-2 text-sm text-shell-danger">
          {error}
        </div>
      ) : null}

      {isLoading ? (
        <RunSkeleton />
      ) : runs.length === 0 ? (
        <SharedStatePanel
          message="Start a source, recording, replay, or scenario to bring active runtime back into view here."
          state="empty"
          title="No active runtime is shown right now."
        />
      ) : (
        <div className="space-y-3">
          {runs.map((run) => (
            <article
              key={run.id}
              className="rounded-md border border-shell-line bg-white px-4 py-4"
            >
              <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="text-sm font-medium text-shell-ink">{run.label}</p>
                  </div>
                  <p className="mt-1 text-sm text-shell-muted">
                    {run.initiator} · {run.startedAt}
                  </p>
                  {run.relatedLabel ? (
                    <p className="mt-0.5 text-xs text-shell-muted">{run.relatedLabel}</p>
                  ) : null}
                </div>

                <div className="flex flex-wrap items-center gap-2">
                  <StatusBadge
                    label={runProcessLabel(run.runState, run.processType)}
                    tone={runStateTone(run.runState)}
                  />
                  <StatusBadge
                    label={run.processType}
                    tone={processTone(run.processType)}
                  />
                </div>
              </div>

              {run.runState === "failed" ? (
                <div className="mt-3 rounded-md border border-shell-danger/30 bg-shell-danger/10 px-3 py-2 text-sm text-shell-danger">
                  This run failed. Open the related source for details.
                </div>
              ) : null}

              {run.relatedSourceId ? (
                <div className="mt-4 flex flex-wrap items-center gap-3">
                  <Link
                    className="shell-text-action"
                    to={`/data-sources/${run.relatedSourceId}`}
                  >
                    Open source
                  </Link>
                </div>
              ) : null}
            </article>
          ))}
        </div>
      )}
    </section>
  );
}
