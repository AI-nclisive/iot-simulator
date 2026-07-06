/**
 * scenarios-store.ts — async API-backed scenarios state (UI-127).
 *
 * Replaces the in-memory mock store with real API calls for scenarios CRUD
 * and validation. Step mutations remain local (synchronous) — call
 * saveScenarioSteps to persist them. Run/stop remain no-ops until IS-141+UI-129.
 */

import { create } from "zustand";
import { apiFetch, ApiError } from "../api";
import type { Page } from "../api";
import {
  STEP_TYPE_LABELS,
  isStepConfigured,
  type ScenarioStep,
  type ScenarioStepType,
} from "../surfaces/scenario-steps";
import {
  toApiStep,
  fromApiStep,
  fromApiScenario,
  stopScenarioRun,
  type ScenarioApiRow,
  type ScenarioApiValidation,
  type ScenarioApiValidationIssue,
} from "./scenarios-api";

export type { ScenarioRow, ScenarioRunState, ScenarioLastRun, ScenarioApiValidationIssue } from "./scenarios-api";

export interface LiveRunState {
  runId: string;
  evidenceId: string | null;
  state: "running" | "stopped" | "completed" | "failed";
  stepOrdinals: Record<number, "active" | "done">;
}

let stepSeq = 1000;

type ScenariosState = {
  scenarios: import("./scenarios-api").ScenarioRow[];
  /** Ordered steps per scenario id (builder shell). */
  steps: Record<string, ScenarioStep[]>;
  /** Per-scenario version for optimistic concurrency (PATCH If-Match). */
  versions: Record<string, number>;
  /** Live run state keyed by scenario id. */
  liveRuns: Record<string, LiveRunState>;
  /** Server-side validation issues per scenario id (fetched after each save). */
  serverIssues: Record<string, ScenarioApiValidationIssue[]>;
  isLoading: boolean;
  error: string | null;

  loadScenarios: (projectId: string) => Promise<void>;
  createScenario: (projectId: string) => Promise<string | null>;
  renameScenario: (projectId: string, id: string, name: string) => Promise<void>;
  duplicateScenario: (projectId: string, id: string) => Promise<string | null>;
  deleteScenario: (projectId: string, id: string) => Promise<void>;
  /** PATCH the scenario with its current local steps. */
  saveScenarioSteps: (projectId: string, id: string) => Promise<void>;

  // Step mutations (local only — call saveScenarioSteps to persist)
  addStep: (scenarioId: string, type: ScenarioStepType) => string;
  updateStep: (
    scenarioId: string,
    stepId: string,
    patch: { label?: string; config?: Record<string, unknown>; configured?: boolean },
  ) => void;
  removeStep: (scenarioId: string, stepId: string) => void;
  moveStep: (scenarioId: string, stepId: string, direction: "up" | "down") => void;

  // Run / stop
  runScenario: (projectId: string, id: string) => Promise<string | null>;
  stopScenario: (projectId: string, id: string, runId: string) => Promise<void>;
  /** Called by the run view when SSE run-finished fires — updates liveRun.state without removing it. */
  onRunFinished: (id: string, state: LiveRunState["state"]) => void;
  /** Called when the run view unmounts — removes liveRuns entry and resets runState. */
  clearLiveRun: (id: string) => void;
};

export const useScenariosStore = create<ScenariosState>((set, get) => ({
  scenarios: [],
  steps: {},
  versions: {},
  liveRuns: {},
  serverIssues: {},
  isLoading: false,
  error: null,

  loadScenarios: async (projectId: string) => {
    set({ isLoading: true, error: null });
    try {
      const page = await apiFetch<Page<ScenarioApiRow>>(
        `/api/v1/projects/${projectId}/scenarios`,
      );
      const scenarios = page.items.map(fromApiScenario);
      const steps: Record<string, ScenarioStep[]> = {};
      const versions: Record<string, number> = {};
      for (const row of page.items) {
        steps[row.id] = row.steps.map(fromApiStep);
        versions[row.id] = row.version;
      }
      set({ scenarios, steps, versions, isLoading: false });
    } catch (err) {
      const message = err instanceof ApiError ? err.title : "Failed to load scenarios";
      set({ error: message, isLoading: false });
    }
  },

  createScenario: async (projectId: string) => {
    try {
      const data = await apiFetch<ScenarioApiRow>(
        `/api/v1/projects/${projectId}/scenarios`,
        {
          method: "POST",
          body: JSON.stringify({ name: "Untitled scenario", steps: [] }),
        },
      );
      const row = fromApiScenario(data);
      set((state) => ({
        scenarios: [...state.scenarios, row],
        steps: { ...state.steps, [data.id]: [] },
        versions: { ...state.versions, [data.id]: data.version },
      }));
      return data.id;
    } catch (err) {
      const message = err instanceof ApiError ? err.title : "Failed to create scenario";
      set({ error: message });
      return null;
    }
  },

  renameScenario: async (projectId: string, id: string, name: string) => {
    const trimmed = name.trim();
    if (!trimmed) return;
    // Capture previous name for rollback
    const prevName = get().scenarios.find((s) => s.id === id)?.name ?? "";
    // Optimistic local update
    set((state) => ({
      scenarios: state.scenarios.map((s) =>
        s.id === id ? { ...s, name: trimmed } : s,
      ),
    }));
    try {
      const version = get().versions[id] ?? 1;
      const data = await apiFetch<ScenarioApiRow>(
        `/api/v1/projects/${projectId}/scenarios/${id}`,
        {
          method: "PATCH",
          headers: { "If-Match": `"${version}"` },
          body: JSON.stringify({ name: trimmed }),
        },
      );
      set((state) => ({
        versions: { ...state.versions, [id]: data.version },
      }));
    } catch (err) {
      // Roll back the optimistic update on failure
      set((state) => ({
        scenarios: state.scenarios.map((s) =>
          s.id === id ? { ...s, name: prevName } : s,
        ),
        error: err instanceof ApiError ? err.title : "Failed to rename scenario",
      }));
    }
  },

  duplicateScenario: async (projectId: string, id: string) => {
    try {
      const data = await apiFetch<ScenarioApiRow>(
        `/api/v1/projects/${projectId}/scenarios/${id}/duplicate`,
        { method: "POST" },
      );
      const row = fromApiScenario(data);
      set((state) => ({
        scenarios: [...state.scenarios, row],
        steps: { ...state.steps, [data.id]: data.steps.map(fromApiStep) },
        versions: { ...state.versions, [data.id]: data.version },
      }));
      return data.id;
    } catch (err) {
      const message = err instanceof ApiError ? err.title : "Failed to duplicate scenario";
      set({ error: message });
      return null;
    }
  },

  deleteScenario: async (projectId: string, id: string) => {
    try {
      await apiFetch<undefined>(
        `/api/v1/projects/${projectId}/scenarios/${id}`,
        { method: "DELETE" },
      );
      set((state) => {
        const steps = { ...state.steps };
        delete steps[id];
        const versions = { ...state.versions };
        delete versions[id];
        return {
          scenarios: state.scenarios.filter((s) => s.id !== id),
          steps,
          versions,
        };
      });
    } catch (err) {
      const message = err instanceof ApiError ? err.title : "Failed to delete scenario";
      set({ error: message });
    }
  },

  saveScenarioSteps: async (projectId: string, id: string) => {
    const localSteps = get().steps[id] ?? [];
    const version = get().versions[id] ?? 1;
    const apiSteps = localSteps.map(toApiStep);
    try {
      const data = await apiFetch<ScenarioApiRow>(
        `/api/v1/projects/${projectId}/scenarios/${id}`,
        {
          method: "PATCH",
          headers: { "If-Match": `"${version}"` },
          body: JSON.stringify({ steps: apiSteps }),
        },
      );
      // Refresh from response to keep versions/stepCount in sync
      const updatedRow = fromApiScenario(data);
      set((state) => ({
        scenarios: state.scenarios.map((s) => (s.id === id ? updatedRow : s)),
        versions: { ...state.versions, [id]: data.version },
      }));
      // Fetch server validation so the builder can surface schema-mismatch and
      // FAULT-not-executable warnings alongside client-side validation.
      try {
        const v = await apiFetch<ScenarioApiValidation>(
          `/api/v1/projects/${projectId}/scenarios/${id}/validate`,
        );
        set((state) => ({
          serverIssues: { ...state.serverIssues, [id]: v.issues },
        }));
      } catch {
        // Validate is best-effort; don't surface a validation-fetch failure as a save error.
      }
    } catch (err) {
      const message = err instanceof ApiError ? err.title : "Failed to save scenario";
      set({ error: message });
      throw err; // re-throw so the UI can show "Save failed"
    }
  },

  // ── Step mutations (synchronous, local only) ─────────────────────────────

  addStep: (scenarioId, type) => {
    const stepId = `st-${++stepSeq}`;
    const configured = isStepConfigured(type, {});
    const step: ScenarioStep = {
      id: stepId,
      type,
      label: STEP_TYPE_LABELS[type],
      config: {},
      configured,
    };
    set((state) => ({
      steps: {
        ...state.steps,
        [scenarioId]: [...(state.steps[scenarioId] ?? []), step],
      },
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

  // ── Run / stop ───────────────────────────────────────────────────────────

  runScenario: async (projectId: string, id: string) => {
    try {
      const data = await apiFetch<{ runId: string; evidenceId: string; status: string }>(
        `/api/v1/projects/${projectId}/scenarios/${id}/run`,
        { method: "POST" },
      );
      set((state) => ({
        liveRuns: {
          ...state.liveRuns,
          [id]: {
            runId: data.runId,
            evidenceId: data.evidenceId ?? null,
            state: "running",
            stepOrdinals: {},
          },
        },
        scenarios: state.scenarios.map((s) =>
          s.id === id ? { ...s, runState: "Running" as const } : s,
        ),
      }));
      return data.runId;
    } catch {
      return null;
    }
  },

  stopScenario: async (projectId: string, id: string, runId: string) => {
    try {
      await stopScenarioRun(projectId, id, runId);
      set((state) => ({
        liveRuns: state.liveRuns[id]
          ? {
              ...state.liveRuns,
              [id]: { ...state.liveRuns[id], state: "stopped" as const },
            }
          : state.liveRuns,
        scenarios: state.scenarios.map((s) =>
          s.id === id ? { ...s, runState: "Stopped" as const } : s,
        ),
      }));
    } catch {
      // Swallow — UI shows stoppedLocally flag regardless
    }
  },

  onRunFinished: (id: string, state: LiveRunState["state"]) => {
    const terminalRunState =
      state === "failed" ? ("Failed" as const)
      : state === "stopped" ? ("Stopped" as const)
      : ("Not running" as const);
    set((prev) => ({
      liveRuns: prev.liveRuns[id]
        ? { ...prev.liveRuns, [id]: { ...prev.liveRuns[id], state } }
        : prev.liveRuns,
      scenarios: prev.scenarios.map((s) =>
        s.id === id ? { ...s, runState: terminalRunState } : s,
      ),
    }));
  },

  clearLiveRun: (id: string) => {
    // Only clears the client-side SSE tracking entry. runState is managed by
    // onRunFinished / stopScenario — clearing it here would reset a still-running
    // scenario's status whenever the user navigates away from the run view.
    set((state) => {
      const liveRuns = { ...state.liveRuns };
      delete liveRuns[id];
      return { liveRuns };
    });
  },
}));
