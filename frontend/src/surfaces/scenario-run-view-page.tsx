/**
 * scenario-run-view-page.tsx — Scenario Run View (UI-065).
 *
 * Read-only observation of one scenario run: run summary, current step, ordered
 * step timeline, involved sources, faults, clients, events, and evidence state.
 * All users can inspect; stopping a run is allowed for User and Admin. Answers
 * "what is the scenario doing right now and what happened just before".
 *
 * Mock-backed via scenario-run.ts until the run API + live streams land.
 */

import { useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useNotificationStore } from "../shell/notification-store";
import { useScenariosStore } from "../shell/scenarios-store";
import { ConfirmationDialog } from "../ui/confirmation-dialog";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge, type StatusTone } from "../ui/status-badge";
import {
  mockRunFor,
  type EvidenceState,
  type RunStatus,
  type RunStepStatus,
} from "./scenario-run";

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
  const pushNotification = useNotificationStore((s) => s.push);

  const [confirmStop, setConfirmStop] = useState(false);
  // Track a local stopped overlay so the Stop action reflects immediately.
  const [stoppedLocally, setStoppedLocally] = useState(false);

  const run = useMemo(
    () => (scenario ? mockRunFor(scenario.id, scenario.name) : null),
    [scenario],
  );

  if (!scenario || !run) {
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

  const status: RunStatus = stoppedLocally ? "stopped" : run.status;
  const isRunning = status === "running";
  const currentStep =
    run.currentStepIndex !== null ? run.timeline[run.currentStepIndex] : null;

  function handleStopConfirmed() {
    stopScenario(scenario!.id);
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
            <StatusBadge label={status} tone={runStatusTone(status)} />
          </div>
          <p className="mt-1 text-sm text-shell-muted">
            Started by {run.initiator} at {formatTime(run.startedAt)}
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
        {currentStep && isRunning ? (
          <p className="mt-1 text-sm font-medium text-shell-ink">
            {currentStep.label}{" "}
            <span className="text-shell-muted">({currentStep.type})</span>
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
          </div>
          <ol className="min-h-0 flex-1 divide-y divide-shell-line overflow-y-auto">
            {run.timeline.map((step, idx) => (
              <li key={step.stepId} className="flex items-center gap-3 px-4 py-2.5">
                <span className="text-xs text-shell-muted">{idx + 1}</span>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-shell-ink">{step.label}</p>
                  <p className="text-xs text-shell-muted">
                    {step.type} · {step.startedAt ? formatTime(step.startedAt) : "not started"}
                  </p>
                </div>
                <StatusBadge label={step.status} tone={stepStatusTone(step.status)} />
              </li>
            ))}
          </ol>
        </section>

        {/* Right column: sources, faults, clients, events, evidence */}
        <div className="flex min-h-0 flex-col gap-4 overflow-y-auto">
          {/* Sources */}
          <section aria-label="Sources involved" className="rounded-lg border border-shell-line bg-white px-4 py-3">
            <h2 className="mb-2 text-sm font-semibold text-shell-ink">Sources</h2>
            {run.sources.map((src) => (
              <div key={src.sourceId} className="flex items-center justify-between py-1">
                <button
                  className="shell-text-action text-left text-sm"
                  type="button"
                  onClick={() => navigate(`/data-sources/${src.sourceId}`)}
                >
                  {src.name}
                </button>
                <StatusBadge
                  label={src.state}
                  tone={src.state === "Active" ? "accent" : src.state === "Error" ? "danger" : "neutral"}
                />
              </div>
            ))}
          </section>

          {/* Faults */}
          {run.faults.length > 0 ? (
            <section aria-label="Faults" className="rounded-lg border border-shell-line bg-white px-4 py-3">
              <h2 className="mb-2 text-sm font-semibold text-shell-ink">Faults</h2>
              {run.faults.map((f) => (
                <div key={f.stepId} className="flex items-center justify-between py-1 text-sm">
                  <span className="text-shell-ink">
                    {f.label} <span className="text-shell-muted">({f.kind})</span>
                  </span>
                  <StatusBadge label={f.active ? "Active" : "Armed"} tone={f.active ? "danger" : "neutral"} />
                </div>
              ))}
            </section>
          ) : null}

          {/* Clients */}
          <section aria-label="Clients" className="rounded-lg border border-shell-line bg-white px-4 py-3">
            <h2 className="mb-2 text-sm font-semibold text-shell-ink">Clients ({run.clients.length})</h2>
            {run.clients.map((c) => (
              <div key={c.clientId} className="flex items-center justify-between py-1 text-sm">
                <span className="font-mono text-shell-ink">{c.clientId}</span>
                <StatusBadge
                  label={c.connected ? "Connected" : "Disconnected"}
                  tone={c.connected ? "accent" : "neutral"}
                />
              </div>
            ))}
          </section>

          {/* Evidence */}
          <section aria-label="Evidence" className="rounded-lg border border-shell-line bg-white px-4 py-3">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-semibold text-shell-ink">Evidence</h2>
              <StatusBadge
                label={evidenceLabel(run.evidence)}
                tone={run.evidence === "available" ? "accent" : run.evidence === "collecting" ? "warning" : "neutral"}
              />
            </div>
            {run.evidence === "available" && run.evidenceId ? (
              <button
                className="shell-text-action mt-2 text-sm"
                type="button"
                onClick={() => navigate(`/evidence/${run.evidenceId}`)}
              >
                Open evidence
              </button>
            ) : (
              <p className="mt-1 text-xs text-shell-muted">
                {run.evidence === "collecting"
                  ? "Evidence is being collected while the run is active."
                  : "No evidence captured for this run."}
              </p>
            )}
          </section>

          {/* Events */}
          <section aria-label="Events" className="rounded-lg border border-shell-line bg-white px-4 py-3">
            <h2 className="mb-2 text-sm font-semibold text-shell-ink">Recent events</h2>
            <ul className="space-y-1.5">
              {run.events.map((ev, i) => (
                <li key={`${ev.at}-${i}`} className="text-sm">
                  <span className="text-shell-muted">{formatTime(ev.at)}</span>{" "}
                  <span className="font-medium text-shell-ink">{ev.type}</span>
                  <span className="text-shell-muted"> — {ev.detail}</span>
                </li>
              ))}
            </ul>
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
