/**
 * scenarios-api.ts — API types and FE↔BE mapper functions for scenarios (UI-127).
 *
 * Pure functions, no state. Bridges the gap between the FE step model
 * (lowercase types, sourceId in config) and the BE contract (UPPERCASE types,
 * targetSourceId at top level, params JSON string).
 */

import { apiFetch } from "../api";
import {
  STEP_TYPE_LABELS,
  isStepConfigured,
  type ScenarioStep,
  type ScenarioStepType,
} from "../surfaces/scenario-steps";

// ── BE shapes ────────────────────────────────────────────────────────────────

export interface ScenarioApiStep {
  ordinal: number;
  type: string; // uppercase: START, STOP, REPLAY, SYNTHETIC, FAULT, WAIT, MARKER
  targetSourceId: string | null;
  params: string; // JSON string
}

export interface ScenarioApiRow {
  id: string;
  projectId: string;
  name: string;
  status: "DRAFT" | "VALID" | "INVALID";
  deterministicSettings: unknown | null;
  steps: ScenarioApiStep[];
  createdAt: string;
  updatedAt: string;
  createdBy: string | undefined;
  version: number;
}

export interface ScenarioApiValidationIssue {
  ordinal: number;
  severity: "ERROR" | "WARNING";
  message: string;
}

export interface ScenarioApiValidation {
  status: "VALID" | "INVALID";
  issues: ScenarioApiValidationIssue[];
}

// ── FE row type (exported so pages can import from here or scenarios-store) ──

export type ScenarioRunState = "Not running" | "Running" | "Stopped" | "Failed";

export interface ScenarioLastRun {
  /** ISO timestamp of the last run, or null if never run. */
  at: string | null;
  outcome: "Completed" | "Stopped" | "Failed" | null;
}

export interface ScenarioRow {
  id: string;
  name: string;
  description: string;
  /** Number of steps configured in the builder. */
  stepCount: number;
  runState: ScenarioRunState;
  lastRun: ScenarioLastRun;
  /** Owner (creator) display name. */
  owner: string;
  /** Display name of whoever currently holds the edit lock, or null. */
  lockedBy: string | null;
  updatedAt: string;
}

// ── FE → BE mapper ────────────────────────────────────────────────────────────

function stepTypeToApi(type: ScenarioStepType): string {
  return type.toUpperCase();
}

/**
 * Map a FE step to the BE StepDto shape.
 * - type: uppercase
 * - targetSourceId: pulled from config.sourceId
 * - params: JSON string with type-specific fields
 */
export function toApiStep(step: ScenarioStep): { type: string; targetSourceId: string | null; params: string } {
  const type = stepTypeToApi(step.type);
  const sourceId = typeof step.config.sourceId === "string" ? step.config.sourceId : null;
  let params: Record<string, unknown> = {};

  switch (step.type) {
    case "wait": {
      const seconds = typeof step.config.seconds === "number" ? step.config.seconds : 0;
      params = { durationMs: seconds * 1000 };
      break;
    }
    case "marker": {
      params = { label: step.config.label ?? "" };
      break;
    }
    case "replay": {
      params = {
        recordingId: step.config.recordingId ?? null,
        compatibilityAck: step.config.compatibilityAck === true,
      };
      break;
    }
    case "synthetic": {
      const seconds = typeof step.config.seconds === "number" ? step.config.seconds : 0;
      params = { durationMs: seconds * 1000 };
      break;
    }
    case "fault": {
      // Pass all fault config as params; the fault model owns its own fields
      const { sourceId: _sid, ...rest } = step.config;
      void _sid;
      params = rest;
      break;
    }
    default:
      // start, stop: no params needed beyond targetSourceId
      params = {};
      break;
  }

  return {
    type,
    targetSourceId: sourceId,
    params: JSON.stringify(params),
  };
}

// ── BE → FE mapper ────────────────────────────────────────────────────────────

function stepTypeFromApi(type: string): ScenarioStepType {
  const lower = type.toLowerCase();
  const validTypes: ScenarioStepType[] = [
    "start", "stop", "replay", "synthetic", "fault", "wait", "marker",
  ];
  return (validTypes as string[]).includes(lower) ? (lower as ScenarioStepType) : "marker";
}

/**
 * Map a BE StepDto to the FE ScenarioStep shape.
 * - id derived from ordinal: "ordinal-{ordinal}"
 * - targetSourceId → config.sourceId
 * - params JSON parsed back to FE field names
 */
export function fromApiStep(row: ScenarioApiStep): ScenarioStep {
  const type = stepTypeFromApi(row.type);
  const id = `ordinal-${row.ordinal}`;

  let parsedParams: Record<string, unknown> = {};
  try {
    parsedParams = JSON.parse(row.params || "{}") as Record<string, unknown>;
  } catch {
    parsedParams = {};
  }

  const config: Record<string, unknown> = {};

  // Restore targetSourceId → sourceId
  if (row.targetSourceId != null) {
    config.sourceId = row.targetSourceId;
  }

  switch (type) {
    case "wait": {
      const durationMs = typeof parsedParams.durationMs === "number" ? parsedParams.durationMs : 0;
      config.seconds = durationMs / 1000;
      break;
    }
    case "marker": {
      config.label = parsedParams.label ?? "";
      break;
    }
    case "replay": {
      config.recordingId = parsedParams.recordingId ?? null;
      config.compatibilityAck = parsedParams.compatibilityAck === true;
      break;
    }
    case "synthetic": {
      const durationMs = typeof parsedParams.durationMs === "number" ? parsedParams.durationMs : 0;
      config.seconds = durationMs / 1000;
      break;
    }
    case "fault": {
      // Merge all parsed params back into config (fault owns its own fields)
      Object.assign(config, parsedParams);
      break;
    }
    default:
      break;
  }

  const configured = isStepConfigured(type, config);

  return {
    id,
    type,
    label: STEP_TYPE_LABELS[type],
    config,
    configured,
  };
}

/**
 * Map a BE ScenarioResponse to the FE ScenarioRow shape.
 * - runState: always "Not running" (live run state comes from active-runs, UI-129)
 * - lastRun: { at: null, outcome: null } (not yet tracked)
 * - owner: createdBy
 * - lockedBy: null (edit-lock not yet implemented)
 * - description: "" (no description field on BE yet)
 * - stepCount: steps.length
 */
export function fromApiScenario(row: ScenarioApiRow): ScenarioRow {
  return {
    id: row.id,
    name: row.name,
    description: "",
    stepCount: row.steps.length,
    runState: "Not running",
    lastRun: { at: null, outcome: null },
    owner: row.createdBy ?? "",
    lockedBy: null,
    updatedAt: row.updatedAt,
  };
}

// ── Live run ──────────────────────────────────────────────────────────────────

export interface ScenarioLiveRunResponse {
  runId: string;
  evidenceId: string;
}

/**
 * Stop a running scenario run.
 * POST /api/v1/projects/{pid}/scenarios/{id}/runs/{runId}/stop → 204
 */
export async function stopScenarioRun(
  projectId: string,
  scenarioId: string,
  runId: string,
): Promise<void> {
  await apiFetch<undefined>(
    `/api/v1/projects/${projectId}/scenarios/${scenarioId}/runs/${runId}/stop`,
    { method: "POST" },
  );
}
