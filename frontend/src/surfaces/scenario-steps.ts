/**
 * scenario-steps.ts — the scenario step model (UI-061 builder shell).
 *
 * One step-editing model covers every supported step type (start, stop, replay,
 * synthetic, fault, wait, marker — UI-061 done-when). The builder shell renders
 * the ordered list, a details panel, and a validation summary against this
 * model; the per-type field editors are UI-062 (and fault specifics UI-063), so
 * this file deliberately keeps step config as an open, type-tagged shape.
 */

import { isFaultConfigured } from "./scenario-faults";

export type ScenarioStepType =
  | "start"
  | "stop"
  | "replay"
  | "synthetic"
  | "fault"
  | "wait"
  | "marker";

export const STEP_TYPE_LABELS: Record<ScenarioStepType, string> = {
  start: "Start source",
  stop: "Stop source",
  replay: "Replay recording",
  synthetic: "Synthetic generation",
  fault: "Inject fault",
  wait: "Wait",
  marker: "Marker",
};

export interface ScenarioStep {
  id: string;
  type: ScenarioStepType;
  /** Short human label shown in the list (e.g. the target source or note). */
  label: string;
  /**
   * Type-specific configuration. Left intentionally open here — the typed field
   * editors per step type are UI-062 / UI-063. A step with incomplete required
   * config is reported by validateScenario as not-yet-runnable.
   */
  config: Record<string, unknown>;
  /** Whether the step's required config is filled in. Set by the step editor. */
  configured: boolean;
}

export interface ScenarioValidationIssue {
  stepId: string | null;
  message: string;
}

// ── Per-type config field specs (UI-062 step editor) ────────────────────────
//
// Each step type declares the fields its editor renders. This is the single
// "same step-editing model" the spec asks for: the editor is generic and driven
// by these specs, so every type is edited the same way. Fault gets a minimal
// target here; its detailed parameters are refined by UI-063.

export type StepFieldKind = "source" | "recording" | "number" | "text" | "select";

export interface StepFieldSpec {
  key: string;
  label: string;
  kind: StepFieldKind;
  required: boolean;
  /** For kind "select": the allowed options. */
  options?: { value: string; label: string }[];
  /** Helper text under the field. */
  hint?: string;
}

export const STEP_FIELD_SPECS: Record<ScenarioStepType, StepFieldSpec[]> = {
  start: [{ key: "sourceId", label: "Target source", kind: "source", required: true }],
  stop: [{ key: "sourceId", label: "Target source", kind: "source", required: true }],
  replay: [
    { key: "sourceId", label: "Target source", kind: "source", required: true },
    { key: "recordingId", label: "Recording", kind: "recording", required: true },
  ],
  synthetic: [
    { key: "sourceId", label: "Target source", kind: "source", required: true },
    {
      key: "pattern",
      label: "Generation pattern",
      kind: "select",
      required: true,
      options: [
        { value: "ramp", label: "Ramp" },
        { value: "sine", label: "Sine" },
        { value: "random", label: "Random walk" },
        { value: "constant", label: "Constant" },
      ],
    },
  ],
  fault: [
    { key: "sourceId", label: "Target source", kind: "source", required: true },
    {
      key: "kind",
      label: "Fault kind",
      kind: "select",
      required: true,
      options: [
        { value: "BAD_VALUE", label: "Bad value" },
        { value: "MISSING_VALUE", label: "Missing value" },
        { value: "DELAY", label: "Delay values" },
        { value: "CONNECTION_DROP", label: "Connection drop" },
        { value: "TIMEOUT", label: "Timeout" },
        { value: "PROTOCOL_ERROR", label: "Protocol error" },
        { value: "SOURCE_UNAVAILABLE", label: "Source unavailable" },
      ],
      hint: "Timing and kind-specific parameters are set below.",
    },
  ],
  wait: [
    {
      key: "seconds",
      label: "Duration (seconds)",
      kind: "number",
      required: true,
      hint: "How long to wait before the next step.",
    },
  ],
  marker: [
    { key: "note", label: "Marker note", kind: "text", required: true, hint: "Shown on the run timeline." },
  ],
};

/**
 * Whether a step's required config fields are all present. The editor uses this
 * (via isStepConfigured) to set step.configured, which feeds validateScenario.
 */
export function isStepConfigured(type: ScenarioStepType, config: Record<string, unknown>): boolean {
  // Fault steps have kind-dependent required params (UI-063), so delegate to the
  // fault model rather than the static field list.
  if (type === "fault") {
    return isFaultConfigured(config);
  }
  return STEP_FIELD_SPECS[type]
    .filter((f) => f.required)
    .every((f) => {
      const v = config[f.key];
      if (v === undefined || v === null) return false;
      if (typeof v === "string") return v.trim().length > 0;
      if (typeof v === "number") return !Number.isNaN(v);
      return true;
    });
}

export interface ScenarioValidation {
  /** No blocking issues — the scenario can run. Warnings do not affect this. */
  ready: boolean;
  /** Blocking problems that prevent running. */
  issues: ScenarioValidationIssue[];
  /** Non-blocking advisories (the scenario can still run). */
  warnings: ScenarioValidationIssue[];
}

/**
 * Validate a scenario's steps into a runnable / not-runnable summary. The shell
 * uses this to drive the draft/invalid/ready states and to tell the user
 * exactly why a scenario is not runnable (UI-061 done-when). Warnings are
 * advisory and never block running.
 */
export function validateScenario(steps: ScenarioStep[]): ScenarioValidation {
  const issues: ScenarioValidationIssue[] = [];
  const warnings: ScenarioValidationIssue[] = [];

  if (steps.length === 0) {
    issues.push({ stepId: null, message: "Add at least one step before running." });
  }

  for (const step of steps) {
    if (!step.configured) {
      issues.push({
        stepId: step.id,
        message: `"${step.label || STEP_TYPE_LABELS[step.type]}" needs configuration.`,
      });
    }
  }

  // ── Semantic lifecycle checks (UI-064) ────────────────────────────────────
  // Walk the steps in order, tracking which sources are running, so we can flag
  // steps that act on a source that is not running at that point. Unconfigured
  // steps are skipped here — they are already reported above, and letting a
  // partially-filled step drive the state machine would produce false
  // negatives (an unconfigured start hiding real errors) or false positives
  // (an unconfigured stop invalidating later valid steps).
  const running = new Set<string>();
  for (const step of steps) {
    if (!step.configured) continue;
    const sourceId = typeof step.config.sourceId === "string" ? step.config.sourceId : null;
    const label = step.label || STEP_TYPE_LABELS[step.type];

    switch (step.type) {
      case "start":
        if (sourceId) {
          if (running.has(sourceId)) {
            issues.push({
              stepId: step.id,
              message: `"${label}" starts a source that is already running.`,
            });
          }
          running.add(sourceId);
        }
        break;
      case "stop":
        if (sourceId) {
          if (!running.has(sourceId)) {
            issues.push({
              stepId: step.id,
              message: `"${label}" stops a source that is not running.`,
            });
          }
          running.delete(sourceId);
        }
        break;
      case "replay":
      case "synthetic":
      case "fault":
        if (sourceId && !running.has(sourceId)) {
          issues.push({
            stepId: step.id,
            message: `"${label}" acts on a source that has not been started in this scenario.`,
          });
        }
        break;
      default:
        break; // wait / marker: no source lifecycle
    }
  }

  // Sources started but never stopped — advisory (the run teardown stops them).
  if (running.size > 0) {
    warnings.push({
      stepId: null,
      message: `${running.size} source${running.size === 1 ? "" : "s"} left running at scenario end; they stop on teardown.`,
    });
  }

  // A scenario that only waits/marks does nothing observable — warn, don't block.
  const hasActionable = steps.some((s) => s.type !== "wait" && s.type !== "marker");
  if (steps.length > 0 && !hasActionable) {
    warnings.push({
      stepId: null,
      message: "Scenario has no start, replay, synthetic, or fault step — it will not produce data.",
    });
  }

  return { ready: issues.length === 0, issues, warnings };
}
