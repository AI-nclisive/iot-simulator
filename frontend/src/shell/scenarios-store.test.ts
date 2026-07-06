/**
 * Tests for useScenariosStore (UI-127 live-API)
 *
 * Mocks apiFetch (hoisted vi.mock).
 * Covers:
 * - loadScenarios: populates scenarios from API response (mapper applied)
 * - loadScenarios error path: sets error state
 * - createScenario: calls POST and adds new scenario
 * - saveScenarioSteps: calls PATCH with correct If-Match header
 * - renameScenario (local): keeps synchronous rename behaviour
 * - addStep / removeStep / moveStep: synchronous local mutations
 */

import { afterEach, beforeEach, describe, expect, it, vi, type MockedFunction } from "vitest";
import { useScenariosStore } from "./scenarios-store";
import type { ScenarioRow } from "./scenarios-store";
import { apiFetch } from "../api";

vi.mock("../api", () => ({
  apiFetch: vi.fn(),
  ApiError: class ApiError extends Error {
    constructor(
      public readonly status: number,
      public readonly title: string,
      public readonly detail: string | undefined,
      public readonly type: string | undefined,
    ) {
      super(title);
      this.name = "ApiError";
    }
  },
}));

const mockApiFetch = apiFetch as MockedFunction<typeof apiFetch>;

function makeScenarioApiRow(overrides: Partial<{
  id: string;
  name: string;
  version: number;
  steps: unknown[];
}> = {}) {
  return {
    id: overrides.id ?? "scn-01",
    projectId: "proj-1",
    name: overrides.name ?? "Morning ramp-up",
    status: "DRAFT" as const,
    deterministicSettings: null,
    steps: overrides.steps ?? [],
    createdAt: "2026-07-01T00:00:00Z",
    updatedAt: "2026-07-01T00:00:00Z",
    createdBy: "testuser",
    version: overrides.version ?? 1,
  };
}

function makeScenarioRow(overrides: Partial<ScenarioRow> & { id: string; name: string }): ScenarioRow {
  return {
    description: "",
    stepCount: 0,
    runState: "Not running",
    lastRun: { at: null, outcome: null },
    owner: "testuser",
    lockedBy: null,
    updatedAt: "2026-07-01T00:00:00Z",
    ...overrides,
  };
}

beforeEach(() => {
  useScenariosStore.setState({
    scenarios: [],
    steps: {},
    versions: {},
    isLoading: false,
    error: null,
  });
  mockApiFetch.mockReset();
});

afterEach(() => {
  vi.clearAllMocks();
});

describe("loadScenarios", () => {
  it("populates scenarios from API response with mapper applied", async () => {
    mockApiFetch.mockResolvedValueOnce({
      items: [
        makeScenarioApiRow({ id: "scn-01", name: "Alpha" }),
        makeScenarioApiRow({ id: "scn-02", name: "Beta" }),
      ],
      nextCursor: null,
      limit: 50,
    });
    await useScenariosStore.getState().loadScenarios("proj-1");
    const { scenarios, isLoading, error } = useScenariosStore.getState();
    expect(scenarios).toHaveLength(2);
    expect(scenarios[0].id).toBe("scn-01");
    expect(scenarios[0].name).toBe("Alpha");
    expect(scenarios[0].runState).toBe("Not running");
    expect(scenarios[0].owner).toBe("testuser");
    expect(isLoading).toBe(false);
    expect(error).toBeNull();
  });

  it("populates steps from API response steps array", async () => {
    mockApiFetch.mockResolvedValueOnce({
      items: [
        makeScenarioApiRow({
          id: "scn-01",
          steps: [
            { ordinal: 0, type: "START", targetSourceId: "src-01", params: "{}" },
            { ordinal: 1, type: "WAIT", targetSourceId: null, params: '{"durationMs":5000}' },
          ],
        }),
      ],
      nextCursor: null,
      limit: 50,
    });
    await useScenariosStore.getState().loadScenarios("proj-1");
    const steps = useScenariosStore.getState().steps["scn-01"];
    expect(steps).toHaveLength(2);
    expect(steps[0].type).toBe("start");
    expect(steps[0].id).toBe("ordinal-0");
    expect(steps[0].config.sourceId).toBe("src-01");
    expect(steps[1].type).toBe("wait");
    expect(steps[1].config.seconds).toBe(5);
  });

  it("sets error state on API failure", async () => {
    mockApiFetch.mockRejectedValueOnce(new Error("Network error"));
    await useScenariosStore.getState().loadScenarios("proj-1");
    const { error, isLoading } = useScenariosStore.getState();
    expect(error).toBeTruthy();
    expect(isLoading).toBe(false);
  });

  it("stores version per scenario", async () => {
    mockApiFetch.mockResolvedValueOnce({
      items: [makeScenarioApiRow({ id: "scn-01", version: 3 })],
      nextCursor: null,
      limit: 50,
    });
    await useScenariosStore.getState().loadScenarios("proj-1");
    expect(useScenariosStore.getState().versions["scn-01"]).toBe(3);
  });
});

describe("createScenario", () => {
  it("calls POST /scenarios and adds new scenario to store", async () => {
    mockApiFetch.mockResolvedValueOnce(makeScenarioApiRow({ id: "scn-new", name: "Untitled scenario" }));
    const newId = await useScenariosStore.getState().createScenario("proj-1");
    expect(mockApiFetch).toHaveBeenCalledWith(
      "/api/v1/projects/proj-1/scenarios",
      expect.objectContaining({ method: "POST" }),
    );
    expect(newId).toBe("scn-new");
    expect(useScenariosStore.getState().scenarios).toHaveLength(1);
    expect(useScenariosStore.getState().scenarios[0].id).toBe("scn-new");
  });

  it("returns null on API failure and sets error", async () => {
    mockApiFetch.mockRejectedValueOnce(new Error("Server error"));
    const newId = await useScenariosStore.getState().createScenario("proj-1");
    expect(newId).toBeNull();
    expect(useScenariosStore.getState().error).toBeTruthy();
  });
});

describe("saveScenarioSteps", () => {
  it("calls PATCH with correct If-Match header and current steps", async () => {
    useScenariosStore.setState({
      scenarios: [makeScenarioRow({ id: "scn-01", name: "Alpha" })],
      steps: {
        "scn-01": [
          { id: "st-1", type: "start", label: "Start source", config: { sourceId: "src-01" }, configured: true },
        ],
      },
      versions: { "scn-01": 2 },
    });
    mockApiFetch.mockResolvedValueOnce(makeScenarioApiRow({ id: "scn-01", version: 3 }));
    await useScenariosStore.getState().saveScenarioSteps("proj-1", "scn-01");
    expect(mockApiFetch).toHaveBeenCalledWith(
      "/api/v1/projects/proj-1/scenarios/scn-01",
      expect.objectContaining({
        method: "PATCH",
        headers: expect.objectContaining({ "If-Match": '"2"' }),
      }),
    );
    // Body should contain steps with uppercase type and targetSourceId
    const callBody = JSON.parse(
      (mockApiFetch.mock.calls[0][1] as RequestInit).body as string,
    );
    expect(callBody.steps[0].type).toBe("START");
    expect(callBody.steps[0].targetSourceId).toBe("src-01");
    // version updated from response
    expect(useScenariosStore.getState().versions["scn-01"]).toBe(3);
  });

  it("throws and sets error on PATCH failure", async () => {
    useScenariosStore.setState({
      scenarios: [makeScenarioRow({ id: "scn-01", name: "Alpha" })],
      steps: { "scn-01": [] },
      versions: { "scn-01": 1 },
    });
    mockApiFetch.mockRejectedValueOnce(new Error("Conflict"));
    await expect(
      useScenariosStore.getState().saveScenarioSteps("proj-1", "scn-01"),
    ).rejects.toThrow();
    expect(useScenariosStore.getState().error).toBeTruthy();
  });
});

describe("renameScenario", () => {
  it("optimistically updates local name and PATCHes backend", async () => {
    useScenariosStore.setState({
      scenarios: [makeScenarioRow({ id: "scn-1", name: "Alpha" })],
      steps: {},
      versions: { "scn-1": 1 },
    });
    mockApiFetch.mockResolvedValueOnce(makeScenarioApiRow({ id: "scn-1", name: "Alpha Renamed", version: 2 }));
    await useScenariosStore.getState().renameScenario("proj-1", "scn-1", "Alpha Renamed");
    const scn = useScenariosStore.getState().scenarios.find((s) => s.id === "scn-1");
    expect(scn?.name).toBe("Alpha Renamed");
    expect(mockApiFetch).toHaveBeenCalledWith(
      "/api/v1/projects/proj-1/scenarios/scn-1",
      expect.objectContaining({ method: "PATCH" }),
    );
  });

  it("does not call API when name is blank", async () => {
    useScenariosStore.setState({
      scenarios: [makeScenarioRow({ id: "scn-1", name: "Alpha" })],
      steps: {},
      versions: { "scn-1": 1 },
    });
    await useScenariosStore.getState().renameScenario("proj-1", "scn-1", "   ");
    expect(mockApiFetch).not.toHaveBeenCalled();
  });
});

describe("addStep / removeStep / moveStep", () => {
  beforeEach(() => {
    useScenariosStore.setState({
      scenarios: [makeScenarioRow({ id: "scn-1", name: "Alpha" })],
      steps: { "scn-1": [] },
      versions: { "scn-1": 1 },
    });
  });

  it("addStep appends a new step", () => {
    const id = useScenariosStore.getState().addStep("scn-1", "wait");
    expect(id).toMatch(/^st-/);
    expect(useScenariosStore.getState().steps["scn-1"]).toHaveLength(1);
    expect(useScenariosStore.getState().steps["scn-1"][0].type).toBe("wait");
  });

  it("removeStep removes the step", () => {
    const id = useScenariosStore.getState().addStep("scn-1", "marker");
    useScenariosStore.getState().removeStep("scn-1", id);
    expect(useScenariosStore.getState().steps["scn-1"]).toHaveLength(0);
  });

  it("moveStep swaps steps", () => {
    const id1 = useScenariosStore.getState().addStep("scn-1", "start");
    const id2 = useScenariosStore.getState().addStep("scn-1", "wait");
    useScenariosStore.getState().moveStep("scn-1", id2, "up");
    const stepIds = useScenariosStore.getState().steps["scn-1"].map((s) => s.id);
    expect(stepIds).toEqual([id2, id1]);
  });
});
