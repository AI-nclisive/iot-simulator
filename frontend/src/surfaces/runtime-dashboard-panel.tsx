import { Link } from "react-router-dom";
import { activeRuns, type RunState } from "../shell/mock-workspace";
import { useShellStore } from "../shell/shell-store";
import { useLiveRuntime } from "../shell/use-live-runtime";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StaleBanner } from "../ui/stale-banner";
import { StatusBadge, type StatusTone } from "../ui/status-badge";

function processTone(processType: "Recording" | "Replay" | "Scenario"): StatusTone {
  if (processType === "Recording") {
    return "warning";
  }

  if (processType === "Scenario") {
    return "neutral";
  }

  return "accent";
}

function evidenceTone(status: "Ready" | "Assembling" | "Retry needed"): StatusTone {
  if (status === "Retry needed") {
    return "danger";
  }

  if (status === "Assembling") {
    return "warning";
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

export function RuntimeDashboardPanel() {
  const projectId = useShellStore((state) => state.currentProjectId);
  // Live runtime stream drives the connection/stale indicator and per-source
  // health summary. The active-run process list (recordings/replays/scenarios)
  // is wired separately under UI-097; here we reflect real-time connection state.
  const { sources, status: liveStatus } = useLiveRuntime(projectId, !!projectId);

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

      {activeRuns.length === 0 ? (
        <SharedStatePanel
          message="Start a source, recording, replay, or scenario to bring active runtime back into view here."
          state="empty"
          title="No active runtime is shown right now."
        />
      ) : (
        <div className="space-y-3">
          {activeRuns.map((run) => (
            <article
              key={run.id}
              className={`rounded-md border bg-white px-4 py-4 ${
                run.runSource === "automation"
                  ? "border-shell-accent/25 bg-shell-accent/[0.02]"
                  : "border-shell-line"
              }`}
            >
              <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="text-sm font-medium text-shell-ink">{run.label}</p>
                    {run.runSource === "automation" ? (
                      <span
                        className="inline-flex items-center rounded-full border border-shell-accent/30 bg-shell-accent/10 px-2 py-0.5 text-xs font-medium text-shell-accent"
                        title="Triggered by an external automation process, not a manual user action"
                      >
                        Automated
                      </span>
                    ) : null}
                  </div>
                  <p className="mt-1 text-sm text-shell-muted">
                    {run.initiator} · {run.startedAt}
                  </p>
                </div>

                <div className="flex flex-wrap items-center gap-2">
                  <StatusBadge
                    label={runProcessLabel(run.runState, run.processType)}
                    tone={runStateTone(run.runState)}
                  />
                  {run.runState !== "queued" ? (
                    <StatusBadge
                      label={`Evidence: ${run.evidence}`}
                      tone={evidenceTone(run.evidence)}
                    />
                  ) : null}
                </div>
              </div>

              {run.runState !== "queued" ? (
                <div className="mt-4 flex flex-wrap items-center gap-3">
                  <p className="text-sm text-shell-muted">
                    {run.parameterCount.toLocaleString()} parameters in this run
                  </p>
                </div>
              ) : null}

              {run.runState === "queued" ? (
                <p className="mt-3 text-sm text-shell-muted">
                  Waiting to start — {run.parameterCount.toLocaleString()} parameters scheduled.
                </p>
              ) : null}


              {run.runState === "failed" ? (
                <div className="mt-3 rounded-md border border-shell-danger/30 bg-shell-danger/10 px-3 py-2 text-sm text-shell-danger">
                  This run failed. Open the related source or evidence for details.
                </div>
              ) : null}

              <div className="mt-4 flex flex-wrap items-center gap-3">
                <Link className="shell-text-action" to={run.relatedPath}>
                  Open source
                </Link>
                {run.evidencePath && run.runState !== "queued" ? (
                  <Link className="shell-text-action" to={run.evidencePath}>
                    Open evidence
                  </Link>
                ) : null}
              </div>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}
