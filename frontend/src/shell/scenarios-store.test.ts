import { beforeEach, describe, expect, it } from "vitest";
import { useScenariosStore } from "./scenarios-store";
import type { ScenarioRow } from "../surfaces/mock-scenarios";

function makeScenario(overrides: Partial<ScenarioRow> & { id: string; name: string }): ScenarioRow {
  return {
    description: "",
    stepCount: 0,
    runState: "Not running",
    lastRun: { at: null, outcome: null },
    owner: "Test User",
    lockedBy: null,
    updatedAt: "2026-07-01T00:00:00Z",
    ...overrides,
  };
}

beforeEach(() => {
  useScenariosStore.setState({
    scenarios: [
      makeScenario({ id: "scn-1", name: "Alpha" }),
      makeScenario({ id: "scn-2", name: "Beta" }),
    ],
    steps: { "scn-1": [], "scn-2": [] },
  });
});

describe("renameScenario", () => {
  it("updates the name of the matching scenario", () => {
    useScenariosStore.getState().renameScenario("scn-1", "Alpha Renamed");
    const scn = useScenariosStore.getState().scenarios.find((s) => s.id === "scn-1");
    expect(scn?.name).toBe("Alpha Renamed");
  });

  it("trims whitespace from the new name", () => {
    useScenariosStore.getState().renameScenario("scn-1", "  Trimmed  ");
    const scn = useScenariosStore.getState().scenarios.find((s) => s.id === "scn-1");
    expect(scn?.name).toBe("Trimmed");
  });

  it("keeps the original name when the new name is blank", () => {
    useScenariosStore.getState().renameScenario("scn-1", "   ");
    const scn = useScenariosStore.getState().scenarios.find((s) => s.id === "scn-1");
    expect(scn?.name).toBe("Alpha");
  });

  it("does not affect other scenarios", () => {
    useScenariosStore.getState().renameScenario("scn-1", "New Alpha");
    const scn2 = useScenariosStore.getState().scenarios.find((s) => s.id === "scn-2");
    expect(scn2?.name).toBe("Beta");
  });

  it("is a no-op when id does not exist", () => {
    useScenariosStore.getState().renameScenario("no-such-id", "Ghost");
    const names = useScenariosStore.getState().scenarios.map((s) => s.name);
    expect(names).toEqual(["Alpha", "Beta"]);
  });
});
