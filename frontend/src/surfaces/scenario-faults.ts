/**
 * scenario-faults.ts — fault configuration model for scenario fault steps (UI-063).
 *
 * A fault step needs more than a target + kind (UI-062): the user must
 * understand target, timing, parameters, and resulting behavior before adding
 * it. This module defines each fault kind, its parameters, shared timing, and a
 * plain-language behavior description so the fault panel can make all of that
 * clear up front.
 *
 * Shapes align with the backend fault model (04_DB_SCHEMA: kind / layer /
 * target / params jsonb; IS-087). Faults are never auto-healed — a fault stays
 * until its duration elapses or the scenario clears it.
 */

export type FaultKind = "drop" | "delay" | "corrupt" | "quality";

export const FAULT_KIND_LABELS: Record<FaultKind, string> = {
  drop: "Drop values",
  delay: "Delay values",
  corrupt: "Corrupt values",
  quality: "Force bad quality",
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
}

/** Per-kind parameter specs (beyond the shared timing fields). */
export const FAULT_PARAM_SPECS: Record<FaultKind, FaultParamSpec[]> = {
  drop: [
    {
      key: "dropRate",
      label: "Drop rate",
      kind: "number",
      required: true,
      unit: "%",
      hint: "Percentage of values silently dropped while active.",
    },
  ],
  delay: [
    {
      key: "delayMs",
      label: "Added latency",
      kind: "number",
      required: true,
      unit: "ms",
      hint: "Extra delay applied to each value.",
    },
  ],
  corrupt: [
    {
      key: "corruptRate",
      label: "Corruption rate",
      kind: "number",
      required: true,
      unit: "%",
      hint: "Percentage of values replaced with corrupted data.",
    },
  ],
  quality: [
    {
      key: "quality",
      label: "Reported quality",
      kind: "select",
      required: true,
      options: [
        { value: "BAD", label: "Bad" },
        { value: "UNCERTAIN", label: "Uncertain" },
      ],
      hint: "Quality flag stamped on values while active.",
    },
  ],
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

function isFilled(v: unknown): boolean {
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
    case "drop": {
      const rate = config.dropRate;
      return `Silently drops ${rate ?? "…"}% of values${timing}. Consumers see gaps, not errors.`;
    }
    case "delay": {
      const ms = config.delayMs;
      return `Adds ${ms ?? "…"} ms of latency to each value${timing}.`;
    }
    case "corrupt": {
      const rate = config.corruptRate;
      return `Replaces ${rate ?? "…"}% of values with corrupted data${timing}.`;
    }
    case "quality": {
      const q = config.quality;
      return `Stamps values with ${q ?? "…"} quality${timing}. Values keep flowing but are flagged.`;
    }
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
