/**
 * Unit tests for validateScenario (UI-061) — the warn-vs-block contract.
 */

import { describe, expect, it } from "vitest";
import { validateScenario, type ScenarioStep } from "./scenario-steps";

function step(over: Partial<ScenarioStep>): ScenarioStep {
  return {
    id: over.id ?? "s1",
    type: over.type ?? "start",
    label: over.label ?? "Step",
    config: over.config ?? {},
    configured: over.configured ?? true,
  };
}

describe("validateScenario", () => {
  it("blocks an empty scenario", () => {
    const v = validateScenario([]);
    expect(v.ready).toBe(false);
    expect(v.issues.some((i) => /at least one step/.test(i.message))).toBe(true);
  });

  it("blocks when a step is unconfigured", () => {
    const v = validateScenario([step({ id: "s1", type: "fault", configured: false, label: "Fault" })]);
    expect(v.ready).toBe(false);
    expect(v.issues.some((i) => /needs configuration/.test(i.message))).toBe(true);
  });

  it("is ready when all steps are configured and at least one is actionable", () => {
    const v = validateScenario([step({ id: "s1", type: "start", configured: true })]);
    expect(v.ready).toBe(true);
    expect(v.issues).toHaveLength(0);
    expect(v.warnings).toHaveLength(0);
  });

  it("warns but does NOT block a wait/marker-only scenario", () => {
    const v = validateScenario([
      step({ id: "s1", type: "wait", configured: true, label: "Hold" }),
      step({ id: "s2", type: "marker", configured: true, label: "Mark" }),
    ]);
    expect(v.ready).toBe(true); // warning must not block
    expect(v.issues).toHaveLength(0);
    expect(v.warnings.some((w) => /will not produce data/.test(w.message))).toBe(true);
  });

  it("an unconfigured wait-only scenario still blocks on the config issue, not the warning", () => {
    const v = validateScenario([step({ id: "s1", type: "wait", configured: false, label: "Hold" })]);
    expect(v.ready).toBe(false);
    expect(v.issues.some((i) => /needs configuration/.test(i.message))).toBe(true);
  });
});
