/**
 * scenario-builder-page.tsx — Scenario Builder shell (UI-061).
 *
 * One coherent editing surface for a scenario: an ordered step list, a step
 * details panel, a validation summary, and save/run structure. The shell owns
 * the layout, ordering, add/remove, selection, validation, and the
 * draft/invalid/ready/locked states. The per-type field editors (start, replay,
 * synthetic, fault, …) are UI-062/UI-063 — the details panel here renders a
 * typed placeholder and an "Edit step" entry point for them.
 *
 * Roles (UI-006 / UI-005): a User can inspect and run a saved scenario but not
 * edit it; an Admin can edit. A scenario locked by another editor is read-only.
 */

import { useMemo, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { resolveAccess } from "../shell/access-policy";
import { useNotificationStore } from "../shell/notification-store";
import { useScenariosStore } from "../shell/scenarios-store";
import { useShellStore } from "../shell/shell-store";
import { ConfirmationDialog } from "../ui/confirmation-dialog";
import { SharedStatePanel } from "../ui/shared-state-panel";
import { StatusBadge, type StatusTone } from "../ui/status-badge";
import { ScenarioStepEditor } from "./scenario-step-editor";
import {
  STEP_TYPE_LABELS,
  validateScenario,
  type ScenarioStepType,
} from "./scenario-steps";

const ADDABLE_STEP_TYPES: ScenarioStepType[] = [
  "start",
  "stop",
  "replay",
  "synthetic",
  "fault",
  "wait",
  "marker",
];

type BuilderStatus = "draft" | "invalid" | "ready" | "locked";

function statusTone(status: BuilderStatus): StatusTone {
  if (status === "ready") return "accent";
  if (status === "invalid") return "warning";
  if (status === "locked") return "danger";
  return "neutral";
}

function statusLabel(status: BuilderStatus): string {
  if (status === "ready") return "Ready to run";
  if (status === "invalid") return "Invalid";
  if (status === "locked") return "Locked";
  return "Draft";
}

const EMPTY_STEPS: ReadonlyArray<never> = [];

export function ScenarioBuilderPage() {
  const { scenarioId = "" } = useParams();
  const navigate = useNavigate();

  const accessMode = useShellStore((state) => state.accessMode);
  const sharedRole = useShellStore((state) => state.sharedRole);
  const access = resolveAccess(accessMode, sharedRole);

  const scenario = useScenariosStore((s) => s.scenarios.find((x) => x.id === scenarioId));
  const steps = useScenariosStore((s) => s.steps[scenarioId] ?? EMPTY_STEPS);
  const addStep = useScenariosStore((s) => s.addStep);
  const updateStep = useScenariosStore((s) => s.updateStep);
  const removeStep = useScenariosStore((s) => s.removeStep);
  const moveStep = useScenariosStore((s) => s.moveStep);
  const runScenario = useScenariosStore((s) => s.runScenario);
  const pushNotification = useNotificationStore((s) => s.push);

  const [selectedStepId, setSelectedStepId] = useState<string | null>(steps[0]?.id ?? null);
  const [confirmRemove, setConfirmRemove] = useState<string | null>(null);
  const [editingName, setEditingName] = useState(false);
  const [nameValue, setNameValue] = useState(scenario?.name ?? "");
  const nameInputRef = useRef<HTMLInputElement>(null);
  const renameScenario = useScenariosStore((s) => s.renameScenario);

  const validation = useMemo(() => validateScenario(steps), [steps]);
  const stepIssueIds = useMemo(
    () => new Set(validation.issues.map((i) => i.stepId).filter((id): id is string => id !== null)),
    [validation],
  );

  // A scenario locked by another editor is read-only here (mock phase: any lock
  // is treated as someone else's — see scenarios-page rationale). An admin with
  // no lock conflict can edit; a user can never edit, only inspect + run.
  const lockedByOther = scenario?.lockedBy != null;
  const canEdit = access.canEditScenario && !lockedByOther;

  const status: BuilderStatus = lockedByOther
    ? "locked"
    : !validation.ready
      ? steps.length === 0
        ? "draft"
        : "invalid"
      : "ready";

  if (!scenario) {
    return (
      <div className="px-4 py-6">
        <SharedStatePanel
          state="empty"
          title="Scenario not found."
          message="This scenario may have been removed. Return to the scenarios list."
        />
        <button className="shell-action mt-4" type="button" onClick={() => navigate("/scenarios")}>
          Back to scenarios
        </button>
      </div>
    );
  }

  const selectedStep = steps.find((s) => s.id === selectedStepId) ?? null;

  function handleAdd(type: ScenarioStepType) {
    const id = addStep(scenarioId, type);
    setSelectedStepId(id);
  }

  function handleRemoveConfirmed(stepId: string) {
    removeStep(scenarioId, stepId);
    if (selectedStepId === stepId) setSelectedStepId(null);
  }

  function handleRun() {
    if (!validation.ready) return;
    runScenario(scenarioId);
    pushNotification({ tone: "success", title: `Started "${scenario!.name}".` });
  }

  function handleSave() {
    // No backend yet; the store is already the source of truth in the mock phase.
    pushNotification({ tone: "success", title: "Scenario saved." });
  }

  return (
    <div className="flex h-full flex-col gap-4 px-4 py-6">
      {/* Header */}
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
            {canEdit && editingName ? (
              <input
                ref={nameInputRef}
                className="shell-field text-xl font-semibold py-0.5"
                value={nameValue}
                autoFocus
                onChange={(e) => setNameValue(e.target.value)}
                onBlur={() => {
                  renameScenario(scenario.id, nameValue);
                  setEditingName(false);
                }}
                onKeyDown={(e) => {
                  if (e.key === "Enter") nameInputRef.current?.blur();
                  if (e.key === "Escape") {
                    setNameValue(scenario.name);
                    setEditingName(false);
                  }
                }}
              />
            ) : (
              <h1
                className={`text-xl font-semibold text-shell-ink ${canEdit ? "cursor-pointer hover:text-shell-accent" : ""}`}
                title={canEdit ? "Click to rename" : undefined}
                onClick={canEdit ? () => { setNameValue(scenario.name); setEditingName(true); } : undefined}
              >
                {scenario.name}
              </h1>
            )}
            <StatusBadge label={statusLabel(status)} tone={statusTone(status)} />
          </div>
          <p className="mt-1 text-sm text-shell-muted">{scenario.description}</p>
        </div>
        <div className="flex gap-2">
          {canEdit ? (
            <button className="shell-action" type="button" onClick={handleSave}>
              Save
            </button>
          ) : null}
          {access.canRunScenario ? (
            <button
              className="shell-action"
              type="button"
              disabled={!validation.ready}
              title={validation.ready ? undefined : "Resolve validation issues to run"}
              onClick={handleRun}
            >
              Run
            </button>
          ) : null}
        </div>
      </header>

      {lockedByOther ? (
        <div className="rounded-md border border-shell-danger/30 bg-shell-danger/5 px-4 py-3 text-sm text-shell-ink">
          This scenario is being edited by {scenario.lockedBy}. You can inspect and run it, but
          not change it.
        </div>
      ) : null}

      {/* Validation summary */}
      {validation.issues.length > 0 ? (
        <section
          aria-label="Validation summary"
          className="rounded-md border border-shell-warning/30 bg-shell-warning/5 px-4 py-3"
        >
          <p className="text-sm font-medium text-shell-ink">
            Not runnable yet — {validation.issues.length} issue
            {validation.issues.length === 1 ? "" : "s"}:
          </p>
          <ul className="mt-2 space-y-1 pl-0 text-sm">
            {validation.issues.map((issue, i) => (
              <li key={`${issue.stepId ?? "scenario"}-${i}`} className="flex gap-2">
                <span aria-hidden className="text-shell-warning">•</span>
                {issue.stepId ? (
                  <button
                    type="button"
                    className="shell-text-action text-left text-shell-muted underline-offset-2 hover:underline"
                    onClick={() => setSelectedStepId(issue.stepId)}
                  >
                    {issue.message}
                  </button>
                ) : (
                  <span className="text-shell-muted">{issue.message}</span>
                )}
              </li>
            ))}
          </ul>
        </section>
      ) : (
        <section
          aria-label="Validation summary"
          className="rounded-md border border-shell-accent/25 bg-shell-accent/5 px-4 py-3 text-sm text-shell-ink"
        >
          All steps are configured. This scenario is ready to run.
        </section>
      )}

      {/* Non-blocking warnings — shown whether or not the scenario is runnable. */}
      {validation.warnings.length > 0 ? (
        <section
          aria-label="Validation warnings"
          className="rounded-md border border-shell-warning/20 bg-shell-warning/5 px-4 py-3"
        >
          <ul className="list-disc space-y-1 pl-5 text-sm text-shell-muted">
            {validation.warnings.map((w, i) => (
              <li key={`warn-${w.stepId ?? "scenario"}-${i}`}>{w.message}</li>
            ))}
          </ul>
        </section>
      ) : null}

      {/* Two-pane: step list + details */}
      <div className="grid min-h-0 flex-1 gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.2fr)]">
        {/* Step list */}
        <section aria-label="Step list" className="flex min-h-0 flex-col rounded-lg border border-shell-line bg-white">
          <div className="flex items-center justify-between border-b border-shell-line px-4 py-3">
            <h2 className="text-sm font-semibold text-shell-ink">Steps ({steps.length})</h2>
          </div>

          <ol className="min-h-0 flex-1 divide-y divide-shell-line overflow-y-auto">
            {steps.length === 0 ? (
              <li className="px-4 py-6 text-sm text-shell-muted">
                No steps yet. {canEdit ? "Add a step to begin." : "Nothing to show."}
              </li>
            ) : (
              steps.map((step, idx) => {
                const isSelected = step.id === selectedStepId;
                return (
                  <li key={step.id}>
                    <div
                      className={`flex items-center gap-2 px-3 py-2.5 ${isSelected ? "bg-shell-base/70" : ""}`}
                    >
                      <button
                        className="min-w-0 flex-1 text-left"
                        type="button"
                        onClick={() => setSelectedStepId(step.id)}
                      >
                        <div className="flex items-center gap-2">
                          <span className="text-xs text-shell-muted">{idx + 1}</span>
                          <span className="truncate text-sm font-medium text-shell-ink">
                            {step.label || STEP_TYPE_LABELS[step.type]}
                          </span>
                          {!step.configured ? (
                            <StatusBadge label="Needs config" tone="warning" />
                          ) : stepIssueIds.has(step.id) ? (
                            <StatusBadge label="Issue" tone="danger" />
                          ) : null}
                        </div>
                        <span className="ml-5 text-xs text-shell-muted">
                          {STEP_TYPE_LABELS[step.type]}
                        </span>
                      </button>

                      {canEdit ? (
                        <div className="flex shrink-0 items-center gap-1">
                          <button
                            aria-label="Move step up"
                            className="shell-text-action px-1 text-xs disabled:opacity-30"
                            type="button"
                            disabled={idx === 0}
                            onClick={() => moveStep(scenarioId, step.id, "up")}
                          >
                            ↑
                          </button>
                          <button
                            aria-label="Move step down"
                            className="shell-text-action px-1 text-xs disabled:opacity-30"
                            type="button"
                            disabled={idx === steps.length - 1}
                            onClick={() => moveStep(scenarioId, step.id, "down")}
                          >
                            ↓
                          </button>
                          <button
                            aria-label="Remove step"
                            className="shell-text-action-danger px-1 text-xs"
                            type="button"
                            onClick={() => setConfirmRemove(step.id)}
                          >
                            ✕
                          </button>
                        </div>
                      ) : null}
                    </div>
                  </li>
                );
              })
            )}
          </ol>

          {canEdit ? (
            <div className="border-t border-shell-line px-3 py-3">
              <p className="mb-2 text-xs font-medium uppercase tracking-wide text-shell-muted">
                Add step
              </p>
              <div className="flex flex-wrap gap-1.5">
                {ADDABLE_STEP_TYPES.map((type) => (
                  <button
                    key={type}
                    className="shell-chip text-xs"
                    type="button"
                    onClick={() => handleAdd(type)}
                  >
                    + {STEP_TYPE_LABELS[type]}
                  </button>
                ))}
              </div>
            </div>
          ) : null}
        </section>

        {/* Details panel */}
        <section aria-label="Step details" className="flex min-h-0 flex-col rounded-lg border border-shell-line bg-white">
          <div className="border-b border-shell-line px-4 py-3">
            <h2 className="text-sm font-semibold text-shell-ink">Step details</h2>
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto px-4 py-4">
            {selectedStep ? (
              <ScenarioStepEditor
                key={selectedStep.id}
                step={selectedStep}
                canEdit={canEdit}
                onChange={(patch) => updateStep(scenarioId, selectedStep.id, patch)}
              />
            ) : (
              <p className="text-sm text-shell-muted">
                Select a step on the left to see its details.
              </p>
            )}
          </div>
        </section>
      </div>

      {confirmRemove ? (
        <ConfirmationDialog
          open
          tone="warning"
          title="Remove this step?"
          message="The step is removed from the scenario. You can add it again afterwards."
          confirmLabel="Remove step"
          reversibilityLabel="You can re-add the step afterwards."
          onConfirm={() => {
            handleRemoveConfirmed(confirmRemove);
            setConfirmRemove(null);
          }}
          onClose={() => setConfirmRemove(null)}
        />
      ) : null}
    </div>
  );
}
