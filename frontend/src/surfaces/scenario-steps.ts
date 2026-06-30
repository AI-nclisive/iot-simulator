/**
 * scenario-steps.ts — the scenario step model (UI-061 builder shell).
 *
 * One step-editing model covers every supported step type (start, stop, replay,
 * synthetic, fault, wait, marker — UI-061 done-when). The builder shell renders
 * the ordered list, a details panel, and a validation summary against this
 * model; the per-type field editors are UI-062 (and fault specifics UI-063), so
 * this file deliberately keeps step config as an open, type-tagged shape.
 */

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

export interface ScenarioValidation {
  /** No blocking issues — the scenario can run. */
  ready: boolean;
  issues: ScenarioValidationIssue[];
}

/**
 * Validate a scenario's steps into a runnable / not-runnable summary. The shell
 * uses this to drive the draft/invalid/ready states and to tell the user
 * exactly why a scenario is not runnable (UI-061 done-when).
 */
export function validateScenario(steps: ScenarioStep[]): ScenarioValidation {
  const issues: ScenarioValidationIssue[] = [];

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

  // A scenario that only waits/marks does nothing observable — warn, don't block.
  const hasActionable = steps.some(
    (s) => s.type !== "wait" && s.type !== "marker",
  );
  if (steps.length > 0 && !hasActionable) {
    issues.push({
      stepId: null,
      message: "Scenario has no start, replay, synthetic, or fault step — it will not produce data.",
    });
  }

  return { ready: issues.length === 0, issues };
}
