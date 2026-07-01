/**
 * scenarios-store.ts — in-memory scenarios state for the landing page (UI-060).
 *
 * Holds the scenario list and the run/stop/duplicate actions the landing page
 * exposes. Backed by the mock fixtures until the scenarios API lands; the store
 * boundary means swapping in real API calls later touches only this file
 * (same approach as data-sources-store).
 */

import { create } from "zustand";
import { scenarioRows, stepsByScenario, type ScenarioRow } from "../surfaces/mock-scenarios";
import {
  STEP_TYPE_LABELS,
  isStepConfigured,
  type ScenarioStep,
  type ScenarioStepType,
} from "../surfaces/scenario-steps";

let duplicateSeq = 100;
let stepSeq = 1000;

type ScenariosState = {
  scenarios: ScenarioRow[];
  /** Ordered steps per scenario id (builder shell, UI-061). */
  steps: Record<string, ScenarioStep[]>;
  runScenario: (id: string) => void;
  stopScenario: (id: string) => void;
  duplicateScenario: (id: string) => string | null;
  createScenario: () => string;
  // Builder step operations
  addStep: (scenarioId: string, type: ScenarioStepType) => string;
  updateStep: (
    scenarioId: string,
    stepId: string,
    patch: { label?: string; config?: Record<string, unknown>; configured?: boolean },
  ) => void;
  removeStep: (scenarioId: string, stepId: string) => void;
  moveStep: (scenarioId: string, stepId: string, direction: "up" | "down") => void;
};

export const useScenariosStore = create<ScenariosState>((set, get) => ({
  scenarios: scenarioRows,
  steps: stepsByScenario,

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
    const sourceSteps = get().steps[id] ?? [];
    set((state) => ({
      scenarios: [...state.scenarios, copy],
      steps: { ...state.steps, [newId]: sourceSteps.map((s) => ({ ...s })) },
    }));
    return newId;
  },

  createScenario: () => {
    const newId = `scn-${++duplicateSeq}`;
    const scenario: ScenarioRow = {
      id: newId,
      name: "Untitled scenario",
      description: "New scenario.",
      stepCount: 0,
      runState: "Idle",
      lastRun: { at: null, outcome: null },
      owner: "You",
      lockedBy: null,
      updatedAt: new Date().toISOString(),
    };
    set((state) => ({
      scenarios: [...state.scenarios, scenario],
      steps: { ...state.steps, [newId]: [] },
    }));
    return newId;
  },

  addStep: (scenarioId, type) => {
    const stepId = `st-${++stepSeq}`;
    // Derive from the same source of truth the editor and validateScenario use,
    // so a freshly-added step with empty config is only "configured" if the type
    // genuinely has no required fields (not a hard-coded per-type assumption).
    const configured = isStepConfigured(type, {});
    const step: ScenarioStep = {
      id: stepId,
      type,
      label: STEP_TYPE_LABELS[type],
      config: {},
      configured,
    };
    set((state) => ({
      steps: { ...state.steps, [scenarioId]: [...(state.steps[scenarioId] ?? []), step] },
    }));
    return stepId;
  },

  removeStep: (scenarioId, stepId) =>
    set((state) => ({
      steps: {
        ...state.steps,
        [scenarioId]: (state.steps[scenarioId] ?? []).filter((s) => s.id !== stepId),
      },
    })),

  updateStep: (scenarioId, stepId, patch) =>
    set((state) => ({
      steps: {
        ...state.steps,
        [scenarioId]: (state.steps[scenarioId] ?? []).map((s) =>
          s.id === stepId
            ? {
                ...s,
                ...(patch.label !== undefined ? { label: patch.label } : {}),
                ...(patch.config !== undefined ? { config: patch.config } : {}),
                ...(patch.configured !== undefined ? { configured: patch.configured } : {}),
              }
            : s,
        ),
      },
    })),

  moveStep: (scenarioId, stepId, direction) =>
    set((state) => {
      const list = state.steps[scenarioId] ?? [];
      const idx = list.findIndex((s) => s.id === stepId);
      if (idx === -1) return {};
      const swap = direction === "up" ? idx - 1 : idx + 1;
      if (swap < 0 || swap >= list.length) return {};
      const next = list.slice();
      [next[idx], next[swap]] = [next[swap], next[idx]];
      return { steps: { ...state.steps, [scenarioId]: next } };
    }),
}));
