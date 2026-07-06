/**
 * scenario-faults.ts — fault configuration model for scenario fault steps (UI-063).
 *
 * A fault step needs more than a target + kind (UI-062): the user must
 * understand target, timing, parameters, and resulting behavior before adding
 * it. This module defines each fault kind, its parameters, shared timing, and a
 * plain-language behavior description so the fault panel can make all of that
 * clear up front.
 *
 * Shapes align with the backend fault model (IS-087):
 *   BAD_VALUE | MISSING_VALUE | DELAY | CONNECTION_DROP | TIMEOUT | PROTOCOL_ERROR | SOURCE_UNAVAILABLE
 * Faults are never auto-healed — a fault stays until its duration elapses or the scenario clears it.
 */

export type FaultKind =
  | "BAD_VALUE"
  | "MISSING_VALUE"
  | "DELAY"
  | "CONNECTION_DROP"
  | "TIMEOUT"
  | "PROTOCOL_ERROR"
  | "SOURCE_UNAVAILABLE";

export const FAULT_KIND_LABELS: Record<FaultKind, string> = {
  BAD_VALUE: "Bad value",
  MISSING_VALUE: "Missing value",
  DELAY: "Delay values",
  CONNECTION_DROP: "Connection drop",
  TIMEOUT: "Timeout",
  PROTOCOL_ERROR: "Protocol error",
  SOURCE_UNAVAILABLE: "Source unavailable",
};

export type FaultParamKind = "number" | "select";

export interface FaultParamSpec {
  key: string;
  label: string;
  kind: FaultParamKind;
  required: boolean;
  unit?: string;
  options?: { value: string; label: string }[];
  hint?: string;
  /** Upper bound for number inputs (e.g. 100 for percentages). */
  max?: number;
}

/** Per-kind parameter specs (beyond the shared timing fields). */
export const FAULT_PARAM_SPECS: Record<FaultKind, FaultParamSpec[]> = {
  BAD_VALUE: [],
  MISSING_VALUE: [],
  DELAY: [
    {
      key: "delayMs",
      label: "Added latency",
      kind: "number",
      required: true,
      unit: "ms",
      hint: "Extra delay applied to each value.",
    },
  ],
  CONNECTION_DROP: [],
  TIMEOUT: [],
  PROTOCOL_ERROR: [],
  SOURCE_UNAVAILABLE: [],
};

/** Shared timing fields for every fault kind. */
export interface FaultTiming {
  /** Seconds to wait after the step starts before the fault activates. */
  startAfterSeconds?: number;
  /** Seconds the fault stays active; 0/undefined means until the scenario clears it. */
  durationSeconds?: number;
}

/** Required keys a fully-configured fault needs, by kind (target + kind + params). */
export function faultRequiredKeys(kind: FaultKind | undefined): string[] {
  const base = ["sourceId", "kind"];
  if (!kind) return base;
  return [...base, ...FAULT_PARAM_SPECS[kind].filter((p) => p.required).map((p) => p.key)];
}

export function isFilled(v: unknown): boolean {
  if (v === undefined || v === null) return false;
  if (typeof v === "string") return v.trim().length > 0;
  if (typeof v === "number") return !Number.isNaN(v);
  return true;
}

/** Whether a fault step's config has target + kind + that kind's required params. */
export function isFaultConfigured(config: Record<string, unknown>): boolean {
  const kind = config.kind as FaultKind | undefined;
  if (!isFilled(config.sourceId) || !isFilled(kind)) return false;
  return FAULT_PARAM_SPECS[kind!]
    .filter((p) => p.required)
    .every((p) => isFilled(config[p.key]));
}

/**
 * Plain-language description of what the configured fault will do, shown before
 * the fault is added so its behavior is understandable (UI-063 done-when).
 */
export function describeFault(config: Record<string, unknown>): string {
  const kind = config.kind as FaultKind | undefined;
  if (!kind) return "Choose a fault kind to see what it will do.";

  const timing = faultTimingSentence(config);
  switch (kind) {
    case "BAD_VALUE":
      return `Injects corrupted/invalid values${timing}. Consumers receive data with bad content.`;
    case "MISSING_VALUE":
      return `Silently drops values${timing}. Consumers see gaps — no data arrives.`;
    case "DELAY": {
      const ms = config.delayMs;
      return `Adds ${ms ?? "…"} ms of latency to each value${timing}.`;
    }
    case "CONNECTION_DROP":
      return `Drops the connection to the source${timing}. No values are delivered until restored.`;
    case "TIMEOUT":
      return `Causes read operations to time out${timing}. Consumers receive timeout errors.`;
    case "PROTOCOL_ERROR":
      return `Injects protocol-level errors${timing}. Consumers receive malformed or unexpected messages.`;
    case "SOURCE_UNAVAILABLE":
      return `Makes the source report as unavailable${timing}. Consumers receive unavailability errors.`;
    default:
      return "";
  }
}

function faultTimingSentence(config: Record<string, unknown>): string {
  const start = config.startAfterSeconds as number | undefined;
  const dur = config.durationSeconds as number | undefined;
  const startPart = start ? `, starting ${start}s in` : "";
  const durPart = dur ? ` for ${dur}s` : " until the scenario clears it";
  return `${startPart}${durPart}`;
}
