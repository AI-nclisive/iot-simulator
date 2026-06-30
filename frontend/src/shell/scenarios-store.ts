/**
 * scenarios-store.ts — in-memory scenarios state for the landing page (UI-060).
 *
 * Holds the scenario list and the run/stop/duplicate actions the landing page
 * exposes. Backed by the mock fixtures until the scenarios API lands; the store
 * boundary means swapping in real API calls later touches only this file
 * (same approach as data-sources-store).
 */

import { create } from "zustand";
import { scenarioRows, type ScenarioRow } from "../surfaces/mock-scenarios";

let duplicateSeq = 100;

type ScenariosState = {
  scenarios: ScenarioRow[];
  runScenario: (id: string) => void;
  stopScenario: (id: string) => void;
  duplicateScenario: (id: string) => string | null;
};

export const useScenariosStore = create<ScenariosState>((set, get) => ({
  scenarios: scenarioRows,

  runScenario: (id) =>
    set((state) => ({
      scenarios: state.scenarios.map((s) =>
        s.id === id ? { ...s, runState: "Running", lastRun: { at: new Date().toISOString(), outcome: null } } : s,
      ),
    })),

  stopScenario: (id) =>
    set((state) => ({
      scenarios: state.scenarios.map((s) =>
        s.id === id
          ? { ...s, runState: "Stopped", lastRun: { at: s.lastRun.at, outcome: "Stopped" } }
          : s,
      ),
    })),

  duplicateScenario: (id) => {
    const source = get().scenarios.find((s) => s.id === id);
    if (!source) return null;
    const newId = `scn-${++duplicateSeq}`;
    const copy: ScenarioRow = {
      ...source,
      id: newId,
      name: `${source.name} (copy)`,
      runState: "Idle",
      lastRun: { at: null, outcome: null },
      lockedBy: null,
      updatedAt: new Date().toISOString(),
    };
    set((state) => ({ scenarios: [...state.scenarios, copy] }));
    return newId;
  },
}));
