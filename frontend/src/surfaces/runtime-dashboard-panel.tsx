import { Link } from "react-router-dom";
import { activeRuns, dashboardStale } from "../shell/mock-workspace";
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

export function RuntimeDashboardPanel() {
  return (
    <section aria-label="Runtime dashboard" className="shell-panel px-5 py-5">
      {dashboardStale ? (
        <StaleBanner message="Dashboard data may be outdated. Refresh the page to see the latest runtime state." />
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
              className="rounded-md border border-shell-line bg-white px-4 py-4"
            >
              <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                <div className="min-w-0">
                  <p className="text-sm font-medium text-shell-ink">{run.sourceName}</p>
                  <p className="mt-1 text-sm text-shell-muted">
                    {run.initiator} • {run.startedAt}
                  </p>
                </div>

                <div className="flex flex-wrap items-center gap-2">
                  <StatusBadge label={run.protocol} />
                  {run.processType ? (
                    <StatusBadge
                      label={run.processType}
                      tone={processTone(run.processType)}
                    />
                  ) : null}
                  <StatusBadge
                    label={`Evidence: ${run.evidence}`}
                    tone={evidenceTone(run.evidence)}
                  />
                </div>
              </div>

              <div className="mt-4 flex flex-wrap items-center gap-3">
                <StatusBadge label={`${run.parameterCount.toLocaleString()} parameters`} />
              </div>

              <div className="mt-4">
                <p className="text-xs font-semibold uppercase tracking-[0.08em] text-shell-muted">
                  Pinned preview
                </p>
                <div className="mt-2 flex flex-wrap gap-2">
                  {run.previewParameters.map((parameter) => (
                    <span
                      key={parameter}
                      className="shell-chip border-shell-line bg-white text-shell-muted"
                    >
                      {parameter}
                    </span>
                  ))}
                  <span className="shell-chip border-shell-line bg-white text-shell-muted">
                    +{run.previewOverflowCount.toLocaleString()} more
                  </span>
                </div>
              </div>

              <div className="mt-4 flex flex-wrap items-center gap-3">
                <Link className="shell-text-action" to={run.sourcePath}>
                  Open {run.sourceName}
                </Link>
                {run.evidencePath ? (
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
