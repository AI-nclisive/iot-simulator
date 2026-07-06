/**
 * scenario-step-editor.tsx — the scenario step editor (UI-062 + UI-128).
 *
 * One generic editing form that adapts to every step type via STEP_FIELD_SPECS,
 * so start/stop/replay/synthetic/fault/wait/marker all fit the same step-editing
 * model (UI-062 done-when). Renders each declared field, validates required
 * fields live, derives a human label, and writes back through the store. Fault
 * exposes only its target + kind here; detailed fault parameters are UI-063.
 *
 * Source and recording pickers are wired to the real project data (UI-128).
 * Recordings for a REPLAY step are filtered to those captured from the selected
 * source. Read-only when the caller cannot edit (role/lock).
 */

import { useEffect, useMemo, useState } from "react";
import { useArtifactsStore } from "../shell/artifacts-store";
import { useDataSourcesStore } from "../shell/data-sources-store";
import { FaultConfigPanel } from "./fault-config-panel";
import {
  STEP_FIELD_SPECS,
  STEP_TYPE_LABELS,
  isStepConfigured,
  type ScenarioStep,
  type StepFieldSpec,
} from "./scenario-steps";

interface StepEditorProps {
  step: ScenarioStep;
  projectId: string;
  canEdit: boolean;
  onChange: (patch: { label: string; config: Record<string, unknown>; configured: boolean }) => void;
  /** Server-side validation error messages for this specific step (from the last save). */
  serverValidationIssues?: string[];
}

/** Derive a concise list label from the configured fields. */
function deriveLabel(
  step: ScenarioStep,
  config: Record<string, unknown>,
  sourceNames: Record<string, string>,
): string {
  const specs = STEP_FIELD_SPECS[step.type];
  const first = specs[0];
  if (!first) return STEP_TYPE_LABELS[step.type];

  if (first.kind === "source" && config.sourceId) {
    const name = sourceNames[config.sourceId as string];
    return name ?? STEP_TYPE_LABELS[step.type];
  }
  if (first.kind === "number" && config[first.key] != null) {
    return `${STEP_TYPE_LABELS[step.type]} ${config[first.key]}s`;
  }
  if (first.kind === "text" && typeof config[first.key] === "string" && config[first.key]) {
    return String(config[first.key]);
  }
  return STEP_TYPE_LABELS[step.type];
}

export function ScenarioStepEditor({ step, projectId, canEdit, onChange, serverValidationIssues }: StepEditorProps) {
  const specs = STEP_FIELD_SPECS[step.type];

  // Local working copy of config so typing feels immediate; committed on change.
  // The parent passes key={step.id}, so this component remounts on step change
  // and useState re-captures the correct initial config — no sync effect needed.
  const [config, setConfig] = useState<Record<string, unknown>>(step.config);

  const dataSources = useDataSourcesStore((s) => s.dataSources);
  const artifacts = useArtifactsStore((s) => s.artifacts);
  const loadRecordings = useArtifactsStore((s) => s.loadRecordings);

  // Ensure recordings are loaded whenever a REPLAY step is open.
  useEffect(() => {
    if (step.type === "replay" && projectId) {
      void loadRecordings(projectId);
    }
  }, [step.type, projectId, loadRecordings]);

  const sourceNames = useMemo(
    () => Object.fromEntries(dataSources.map((s) => [s.id, s.name])),
    [dataSources],
  );

  const sourceOptions = useMemo(
    () => dataSources.map((s) => ({ value: s.id, label: s.name })),
    [dataSources],
  );

  // For REPLAY: filter recordings to those captured from the selected source.
  const recordingOptions = useMemo(() => {
    const selectedSourceId = typeof config.sourceId === "string" ? config.sourceId : null;
    return artifacts
      .filter((a) => !selectedSourceId || a.sourceId === selectedSourceId)
      .map((a) => {
        const srcName = a.sourceId ? sourceNames[a.sourceId] ?? a.sourceId : "";
        const label = srcName ? `${a.id} — ${srcName}` : a.id;
        return { value: a.id, label };
      });
  }, [artifacts, config.sourceId, sourceNames]);

  function commit(next: Record<string, unknown>) {
    setConfig(next);
    const configured = isStepConfigured(step.type, next);
    onChange({
      label: deriveLabel(step, next, sourceNames),
      config: next,
      configured,
    });
  }

  function setField(key: string, value: unknown) {
    commit({ ...config, [key]: value });
  }

  // When source changes on a REPLAY step, clear the recording so an incompatible
  // recording is not silently carried over.
  function handleSourceChange(key: string, value: string) {
    if (step.type === "replay" && key === "sourceId") {
      commit({ ...config, [key]: value || undefined, recordingId: undefined });
    } else {
      setField(key, value || undefined);
    }
  }

  function optionsFor(field: StepFieldSpec): { value: string; label: string }[] {
    if (field.kind === "source") return sourceOptions;
    if (field.kind === "recording") return recordingOptions;
    return field.options ?? [];
  }

  return (
    <div className="space-y-4">
      <div>
        <p className="text-xs uppercase tracking-wide text-shell-muted">Type</p>
        <p className="text-sm font-medium text-shell-ink">{STEP_TYPE_LABELS[step.type]}</p>
      </div>

      {specs.length === 0 ? (
        <p className="text-sm text-shell-muted">This step type has no configuration.</p>
      ) : (
        specs.map((field) => {
          const value = config[field.key];
          const fieldId = `step-${step.id}-${field.key}`;
          const showError = field.required && !isFieldFilled(value);

          return (
            <div key={field.key}>
              {field.kind === "checkbox" ? (
                <label className="flex items-center gap-2 text-sm text-shell-ink">
                  <input
                    id={fieldId}
                    type="checkbox"
                    disabled={!canEdit}
                    checked={value === true}
                    onChange={(e) => setField(field.key, e.target.checked)}
                  />
                  {field.label}
                </label>
              ) : (
                <label
                  htmlFor={fieldId}
                  className="mb-1 block text-xs uppercase tracking-wide text-shell-muted"
                >
                  {field.label}
                  {field.required ? <span className="text-shell-danger"> *</span> : null}
                </label>
              )}

              {field.kind === "number" ? (
                <input
                  id={fieldId}
                  className="shell-field w-full"
                  type="number"
                  min={0}
                  disabled={!canEdit}
                  value={typeof value === "number" ? value : (value as string) ?? ""}
                  onChange={(e) =>
                    setField(
                      field.key,
                      e.target.value === "" ? undefined : Math.max(0, Number(e.target.value)),
                    )
                  }
                />
              ) : field.kind === "text" ? (
                <input
                  id={fieldId}
                  className="shell-field w-full"
                  type="text"
                  disabled={!canEdit}
                  value={(value as string) ?? ""}
                  onChange={(e) => setField(field.key, e.target.value)}
                />
              ) : field.kind === "checkbox" ? null : (
                <select
                  id={fieldId}
                  className="shell-field w-full"
                  disabled={!canEdit}
                  value={(value as string) ?? ""}
                  onChange={(e) =>
                    field.kind === "source"
                      ? handleSourceChange(field.key, e.target.value)
                      : setField(field.key, e.target.value || undefined)
                  }
                >
                  <option value="">Select…</option>
                  {optionsFor(field).map((opt) => (
                    <option key={opt.value} value={opt.value}>
                      {opt.label}
                    </option>
                  ))}
                </select>
              )}

              {field.hint ? (
                <p className="mt-1 text-xs text-shell-muted">{field.hint}</p>
              ) : null}
              {showError ? (
                <p className="mt-1 text-xs text-shell-warning">This field is required.</p>
              ) : null}
            </div>
          );
        })
      )}

      {/* Fault steps get kind-specific params, timing, and a behavior preview. */}
      {step.type === "fault" ? (
        <FaultConfigPanel
          config={config}
          canEdit={canEdit}
          onChange={(key, value) => setField(key, value)}
        />
      ) : null}

      {isStepConfigured(step.type, config) ? (
        <p className="text-sm text-shell-accent">Step is fully configured.</p>
      ) : (
        <p className="text-sm text-shell-warning">This step still needs required fields.</p>
      )}

      {serverValidationIssues && serverValidationIssues.length > 0 ? (
        <ul className="mt-2 space-y-1">
          {serverValidationIssues.map((msg, i) => (
            <li key={i} className="text-sm text-shell-danger">
              {msg}
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}

function isFieldFilled(v: unknown): boolean {
  if (v === undefined || v === null) return false;
  if (typeof v === "string") return v.trim().length > 0;
  if (typeof v === "number") return !Number.isNaN(v);
  return true;
}
