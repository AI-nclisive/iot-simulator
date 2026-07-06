/**
 * scenario-run-view-page.tsx — Scenario Run View (UI-129).
 *
 * Live observation of one scenario run: run summary, current step, ordered
 * step timeline, events, and evidence state. Subscribes to the SSE stream
 * for step-started / step-completed / run-finished events. Stopping a run
 * calls POST /…/runs/{runId}/stop via the store.
 */

import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useNotificationStore } from "../shell/notification-store";
import { useScenariosStore } from "../shell/scenarios-store";
import { useShellStore } from "../shell/shell-store";
import { ConfirmationDialog } from "../ui/confirmation-dialog";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge, type StatusTone } from "../ui/status-badge";

type RunStatus = "queued" | "running" | "stopped" | "failed" | "completed" | "stale";
type RunStepStatus = "pending" | "active" | "done" | "skipped" | "failed";
type EvidenceState = "none" | "collecting" | "available";


function runStatusTone(status: RunStatus): StatusTone {
  switch (status) {
    case "running":
      return "accent";
    case "completed":
      return "accent";
    case "failed":
      return "danger";
    case "stale":
      return "warning";
    case "stopped":
      return "warning";
    default:
      return "neutral"; // queued
  }
}

function stepStatusTone(status: RunStepStatus): StatusTone {
  switch (status) {
    case "active":
      return "accent";
    case "done":
      return "neutral";
    case "failed":
      return "danger";
    case "skipped":
      return "warning";
    default:
      return "neutral"; // pending
  }
}

function evidenceLabel(state: EvidenceState): string {
  if (state === "available") return "Available";
  if (state === "collecting") return "Collecting…";
  return "None";
}

function formatTime(iso: string | null): string {
  if (!iso) return "—";
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleTimeString("en-GB", { hour12: false });
}

export function ScenarioRunViewPage() {
  const { scenarioId = "" } = useParams();
  const navigate = useNavigate();

  const scenario = useScenariosStore((s) => s.scenarios.find((x) => x.id === scenarioId));
  const stopScenario = useScenariosStore((s) => s.stopScenario);
  const onRunFinished = useScenariosStore((s) => s.onRunFinished);
  const clearLiveRun = useScenariosStore((s) => s.clearLiveRun);
  const liveRuns = useScenariosStore((s) => s.liveRuns);
  const steps = useScenariosStore((s) => s.steps);
  const currentProjectId = useShellStore((s) => s.currentProjectId);
  const pushNotification = useNotificationStore((s) => s.push);

  const liveRun = liveRuns[scenarioId];

  const [confirmStop, setConfirmStop] = useState(false);
  // Track a local stopped overlay so the Stop action reflects immediately.
  const [stoppedLocally, setStoppedLocally] = useState(false);
  const [stepOrdinals, setStepOrdinals] = useState<Record<number, "active" | "done">>({});
  const [finalState, setFinalState] = useState<"running" | "stopped" | "completed" | "failed">("running");
  const [sseStale, setSseStale] = useState(false);
  const staleTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Reset SSE state when liveRun changes (new run started)
  useEffect(() => {
    setStepOrdinals({});
    setFinalState("running");
    setSseStale(false);
    setStoppedLocally(false);
  }, [liveRun?.runId]);

  // Subscribe to SSE stream when we have a live run
  useEffect(() => {
    if (!liveRun || !currentProjectId) return;

    const url = `/api/v1/projects/${currentProjectId}/scenarios/${scenarioId}/runs/${liveRun.runId}/events`;
    const es = new EventSource(url);

    function resetStaleTimer() {
      if (staleTimerRef.current !== null) {
        clearTimeout(staleTimerRef.current);
      }
      staleTimerRef.current = setTimeout(() => {
        setSseStale(true);
      }, 5000);
    }

    resetStaleTimer();

    es.addEventListener("step-started", (e: MessageEvent) => {
      resetStaleTimer();
      setSseStale(false);
      try {
        const data = JSON.parse(e.data as string) as { ordinal: number; type: string };
        setStepOrdinals((prev) => ({ ...prev, [data.ordinal]: "active" }));
      } catch {
        // ignore parse errors
      }
    });

    es.addEventListener("step-completed", (e: MessageEvent) => {
      resetStaleTimer();
      setSseStale(false);
      try {
        const data = JSON.parse(e.data as string) as { ordinal: number; type: string };
        setStepOrdinals((prev) => ({ ...prev, [data.ordinal]: "done" }));
      } catch {
        // ignore parse errors
      }
    });

    es.addEventListener("run-finished", (e: MessageEvent) => {
      resetStaleTimer();
      setSseStale(false);
      try {
        const data = JSON.parse(e.data as string) as { state: string };
        const s = data.state?.toLowerCase();
        if (s === "completed" || s === "stopped" || s === "failed") {
          setFinalState(s as "completed" | "stopped" | "failed");
          onRunFinished(scenarioId, s as "completed" | "stopped" | "failed");
        }
      } catch {
        // ignore parse errors
      }
      es.close();
    });

    return () => {
      if (staleTimerRef.current !== null) {
        clearTimeout(staleTimerRef.current);
      }
      es.close();
      // Clear the live-run entry so the scenarios list no longer shows "Running".
      clearLiveRun(scenarioId);
    };
  }, [liveRun?.runId, currentProjectId, scenarioId]);

  // No active run — show placeholder panel
  if (!scenario) {
    return (
      <div className="px-4 py-6">
        <SharedStatePanel
          state="empty"
          title="Scenario run not found."
          message="This scenario may have been removed. Return to the scenarios list."
        />
        <button className="shell-action mt-4" type="button" onClick={() => navigate("/scenarios")}>
          Back to scenarios
        </button>
      </div>
    );
  }

  if (!liveRun) {
    return (
      <div className="px-4 py-6">
        <SharedStatePanel
          state="empty"
          title="No active run."
          message="This scenario is not currently running. Start a run from the scenarios list."
        />
        <button className="shell-action mt-4" type="button" onClick={() => navigate("/scenarios")}>
          Back to scenarios
        </button>
      </div>
    );
  }

  // Derive display status
  const displayStatus: RunStatus = (() => {
    if (stoppedLocally || liveRun.state === "stopped" || finalState === "stopped") return "stopped";
    if (finalState === "completed") return "completed";
    if (finalState === "failed") return "failed";
    if (sseStale) return "stale";
    return "running";
  })();

  const isRunning = displayStatus === "running" || displayStatus === "stale";

  // Build timeline from store steps (ordinal = array index)
  const scenarioSteps = steps[scenarioId] ?? [];
  const timeline = scenarioSteps.map((step, idx) => {
    const ordinalState = stepOrdinals[idx];
    const stepStatus: RunStepStatus = ordinalState === "active" ? "active" : ordinalState === "done" ? "done" : "pending";
    return { step, stepStatus, idx };
  });

  const currentStepEntry = timeline.find((t) => t.stepStatus === "active") ?? null;

  // Evidence state
  const evidenceState: EvidenceState = (() => {
    if (liveRun.evidenceId && (displayStatus === "completed" || displayStatus === "stopped")) {
      return "available";
    }
    if (displayStatus === "running" || displayStatus === "stale") {
      return "collecting";
    }
    return "none";
  })();

  function handleStopConfirmed() {
    void stopScenario(currentProjectId, scenarioId, liveRun!.runId);
    setStoppedLocally(true);
    pushNotification({ tone: "success", title: `Stopped "${scenario!.name}".` });
  }

  return (
    <div className="flex h-full flex-col gap-4 px-4 py-6">
      {/* Header / run summary */}
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <button
            className="shell-text-action mb-1 pl-0 text-xs"
            type="button"
            onClick={() => navigate("/scenarios")}
          >
            ← Scenarios
          </button>
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-xl font-semibold text-shell-ink">{scenario.name}</h1>
            <StatusBadge label={displayStatus} tone={runStatusTone(displayStatus)} />
          </div>
          <p className="mt-1 text-sm text-shell-muted">
            Run ID: {liveRun.runId}
          </p>
        </div>
        <div className="flex gap-2">
          <button
            className="shell-action"
            type="button"
            onClick={() => navigate(`/scenarios/${scenario.id}`)}
          >
            Open in builder
          </button>
          {isRunning ? (
            <button
              className="shell-action-danger"
              type="button"
              onClick={() => setConfirmStop(true)}
            >
              Stop run
            </button>
          ) : null}
        </div>
      </header>

      {/* Current step callout */}
      <section
        aria-label="Current step"
        className="rounded-md border border-shell-accent/25 bg-shell-accent/5 px-4 py-3"
      >
        <p className="text-xs uppercase tracking-wide text-shell-muted">Current step</p>
        {currentStepEntry && isRunning ? (
          <p className="mt-1 text-sm font-medium text-shell-ink">
            {currentStepEntry.step.label}{" "}
            <span className="text-shell-muted">({currentStepEntry.step.type})</span>
          </p>
        ) : (
          <p className="mt-1 text-sm text-shell-muted">
            {isRunning ? "Starting…" : "The run is not active."}
          </p>
        )}
      </section>

      <div className="grid min-h-0 flex-1 gap-4 lg:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)]">
        {/* Timeline */}
        <section aria-label="Step timeline" className="flex min-h-0 flex-col rounded-lg border border-shell-line bg-white">
          <div className="border-b border-shell-line px-4 py-3">
            <h2 className="text-sm font-semibold text-shell-ink">Step timeline</h2>
            {sseStale && isRunning ? (
              <p className="mt-1 text-xs text-shell-warning">
                Live updates paused — connection may be stale.
              </p>
            ) : null}
          </div>
          {timeline.length === 0 ? (
            <p className="px-4 py-3 text-sm text-shell-muted">No steps in this scenario.</p>
          ) : (
            <ol className="min-h-0 flex-1 divide-y divide-shell-line overflow-y-auto">
              {timeline.map(({ step, stepStatus, idx }) => (
                <li key={step.id} className="flex items-center gap-3 px-4 py-2.5">
                  <span className="text-xs text-shell-muted">{idx + 1}</span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-shell-ink">{step.label}</p>
                    <p className="text-xs text-shell-muted">{step.type}</p>
                  </div>
                  <StatusBadge label={stepStatus} tone={stepStatusTone(stepStatus)} />
                </li>
              ))}
            </ol>
          )}
        </section>

        {/* Right column: sources, events, evidence */}
        <div className="flex min-h-0 flex-col gap-4 overflow-y-auto">
          {/* Sources */}
          <section aria-label="Sources involved" className="rounded-lg border border-shell-line bg-white px-4 py-3">
            <h2 className="mb-2 text-sm font-semibold text-shell-ink">Sources</h2>
            <p className="text-sm text-shell-muted">No source data during this run.</p>
          </section>

          {/* Evidence */}
          <section aria-label="Evidence" className="rounded-lg border border-shell-line bg-white px-4 py-3">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-semibold text-shell-ink">Evidence</h2>
              <StatusBadge
                label={evidenceLabel(evidenceState)}
                tone={evidenceState === "available" ? "accent" : evidenceState === "collecting" ? "warning" : "neutral"}
              />
            </div>
            {evidenceState === "available" && liveRun.evidenceId ? (
              <button
                className="shell-text-action mt-2 text-sm"
                type="button"
                onClick={() => navigate(`/evidence/${liveRun.evidenceId}`)}
              >
                Open evidence
              </button>
            ) : (
              <p className="mt-1 text-xs text-shell-muted">
                {evidenceState === "collecting"
                  ? "Evidence is being collected while the run is active."
                  : "No evidence captured for this run."}
              </p>
            )}
          </section>

          {/* Events */}
          <section aria-label="Events" className="rounded-lg border border-shell-line bg-white px-4 py-3">
            <h2 className="mb-2 text-sm font-semibold text-shell-ink">Recent events</h2>
            <p className="text-sm text-shell-muted">
              No events captured.
            </p>
          </section>
        </div>
      </div>

      {confirmStop ? (
        <ConfirmationDialog
          open
          tone="warning"
          title={`Stop "${scenario.name}"?`}
          message="The scenario run will stop. Any in-progress steps are interrupted."
          confirmLabel="Stop run"
          reversibilityLabel="You can start the scenario again afterwards."
          onConfirm={() => {
            handleStopConfirmed();
            setConfirmStop(false);
          }}
          onClose={() => setConfirmStop(false)}
        />
      ) : null}
    </div>
  );
}
