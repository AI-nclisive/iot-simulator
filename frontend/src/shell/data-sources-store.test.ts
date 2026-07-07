/**
 * Tests for useDataSourcesStore (UI-097 live-API)
 *
 * Mocks apiFetch and the mapper functions.
 * Covers:
 * - loadDataSources: populates dataSources from API
 * - loadDataSources error path: sets error state
 * - deleteDataSource: removes from store after DELETE
 * - startDataSource: updates row from API response
 * - duplicateDataSource: calls POST /data-sources/{id}/duplicate (UI-104)
 */

import { afterEach, beforeEach, describe, expect, it, vi, type MockedFunction } from "vitest";
import { useDataSourcesStore } from "./data-sources-store";
import { apiFetch, mapProtocol, mapRuntimeStateToStatus, mapRuntimeStateToHealth } from "../api";

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
  mapProtocol: vi.fn((p: string) => (p === "OPC_UA" ? "OPC UA" : "Modbus TCP")),
  mapRuntimeStateToStatus: vi.fn((s: string) => (s === "RUNNING" ? "Active" : "Stopped")),
  mapRuntimeStateToHealth: vi.fn((s: string) => {
    if (s === "RUNNING") return "Healthy";
    if (s === "ERROR") return "Error";
    return null;
  }),
  mapDataType: vi.fn(),
}));

const mockApiFetch = apiFetch as MockedFunction<typeof apiFetch>;
// Satisfy TS — mock implementations are in the factory above
const _mapProtocol = mapProtocol as MockedFunction<typeof mapProtocol>;
const _mapRuntimeStateToStatus = mapRuntimeStateToStatus as MockedFunction<typeof mapRuntimeStateToStatus>;
const _mapRuntimeStateToHealth = mapRuntimeStateToHealth as MockedFunction<typeof mapRuntimeStateToHealth>;

void _mapProtocol;
void _mapRuntimeStateToStatus;
void _mapRuntimeStateToHealth;

function makeDataSourceResponse(overrides: Partial<{
  id: string;
  name: string;
  runtimeState: string;
  basis: "SCAN" | "MANUAL" | "IMPORT" | "SYNTHETIC";
  simulatorPort: number;
  realDeviceEndpoint: string | null;
  serveUrl: string;
  parameterCount: number;
}> = {}) {
  return {
    id: overrides.id ?? "src-01",
    projectId: "proj-1",
    name: overrides.name ?? "Line A",
    protocol: "OPC_UA" as const,
    basis: overrides.basis ?? ("SCAN" as const),
    schemaId: null,
    schemaVersion: null,
    simulatorPort: overrides.simulatorPort ?? 4840,
    realDeviceEndpoint: overrides.realDeviceEndpoint !== undefined ? overrides.realDeviceEndpoint : "opc.tcp://device:4840",
    serveUrl: overrides.serveUrl ?? "opc.tcp://localhost:4840",
    runtimeConfig: null,
    enabled: true,
    runtimeState: (overrides.runtimeState ?? "STOPPED") as "STOPPED",
    credentialState: "NONE",
    createdAt: "2026-06-01T00:00:00Z",
    updatedAt: "2026-06-01T00:00:00Z",
    createdBy: "testuser",
    version: 1,
    ...(overrides.parameterCount !== undefined ? { parameterCount: overrides.parameterCount } : {}),
  };
}

beforeEach(() => {
  useDataSourcesStore.setState({ dataSources: [], isLoading: false, error: null, currentProjectId: "" });
  mockApiFetch.mockReset();
});

afterEach(() => {
  vi.clearAllMocks();
});

describe("loadDataSources", () => {
  it("populates dataSources from API response", async () => {
    mockApiFetch.mockResolvedValueOnce({
      items: [
        makeDataSourceResponse({ id: "src-01", name: "Source A" }),
        makeDataSourceResponse({ id: "src-02", name: "Source B" }),
      ],
      nextCursor: null,
      limit: 50,
    });
    await useDataSourcesStore.getState().loadDataSources("proj-1");
    const { dataSources, isLoading, error } = useDataSourcesStore.getState();
    expect(dataSources).toHaveLength(2);
    expect(dataSources[0].id).toBe("src-01");
    expect(isLoading).toBe(false);
    expect(error).toBeNull();
  });

  it("sets error on API failure", async () => {
    mockApiFetch.mockRejectedValueOnce(new Error("Server error"));
    await useDataSourcesStore.getState().loadDataSources("proj-1");
    const { error, isLoading } = useDataSourcesStore.getState();
    expect(error).toBeTruthy();
    expect(isLoading).toBe(false);
  });

  it("stores currentProjectId", async () => {
    mockApiFetch.mockResolvedValueOnce({ items: [], nextCursor: null, limit: 50 });
    await useDataSourcesStore.getState().loadDataSources("proj-42");
    expect(useDataSourcesStore.getState().currentProjectId).toBe("proj-42");
  });
});

describe("deleteDataSource", () => {
  it("removes the row from dataSources after DELETE", async () => {
    useDataSourcesStore.setState({
      dataSources: [
        { id: "src-01", name: "Source A", protocol: "OPC UA", endpoint: "opc.tcp://localhost:4840", parameterCount: 0, status: "Stopped", health: "Healthy" },
      ],
      currentProjectId: "proj-1",
    });
    mockApiFetch.mockResolvedValueOnce(undefined);
    await useDataSourcesStore.getState().deleteDataSource("src-01", "proj-1");
    expect(useDataSourcesStore.getState().dataSources).toHaveLength(0);
  });
});

describe("duplicateDataSource", () => {
  it("calls POST /data-sources/{rowId}/duplicate and appends the copy", async () => {
    useDataSourcesStore.setState({
      dataSources: [
        { id: "src-01", name: "Source A", protocol: "OPC UA", endpoint: "opc.tcp://localhost:4840", parameterCount: 0, status: "Stopped", health: "Healthy" },
      ],
      currentProjectId: "proj-1",
    });
    mockApiFetch.mockResolvedValueOnce(makeDataSourceResponse({ id: "src-02", name: "Source A (copy)" }));
    await useDataSourcesStore.getState().duplicateDataSource("src-01", "proj-1");
    expect(mockApiFetch).toHaveBeenCalledWith(
      "/api/v1/projects/proj-1/data-sources/src-01/duplicate",
      expect.objectContaining({ method: "POST" }),
    );
    expect(useDataSourcesStore.getState().dataSources).toHaveLength(2);
    expect(useDataSourcesStore.getState().dataSources[1].id).toBe("src-02");
  });
});

describe("createSyntheticSource (IS-145)", () => {
  it("POSTs to /data-sources/synthetic with the config + schemaFromSourceId and appends the row", async () => {
    useDataSourcesStore.setState({ dataSources: [], currentProjectId: "proj-1" });
    mockApiFetch.mockResolvedValueOnce(
      makeDataSourceResponse({ id: "syn-1", name: "Synthetic A", basis: "SYNTHETIC" }),
    );
    const config = {
      seed: 7,
      variables: [
        { nodeId: "temp", dataType: "FLOAT64", pattern: { type: "SINE" as const, min: 0, max: 10, periodMs: 1000 }, updateRateMs: 250 },
      ],
    };
    const id = await useDataSourcesStore.getState().createSyntheticSource({
      projectId: "proj-1",
      name: "Synthetic A",
      protocol: "OPC UA",
      simulatorPort: 4840,
      config,
      schemaFromSourceId: "src-01",
    });
    expect(id).toBe("syn-1");
    expect(mockApiFetch).toHaveBeenCalledWith(
      "/api/v1/projects/proj-1/data-sources/synthetic",
      expect.objectContaining({ method: "POST" }),
    );
    const body = JSON.parse((mockApiFetch.mock.calls[0][1] as { body: string }).body);
    expect(body.schemaFromSourceId).toBe("src-01");
    expect(body.config.variables[0].nodeId).toBe("temp");
    expect(useDataSourcesStore.getState().dataSources.map((r) => r.id)).toContain("syn-1");
  });

  it("omits schemaFromSourceId when not provided", async () => {
    useDataSourcesStore.setState({ dataSources: [], currentProjectId: "proj-1" });
    mockApiFetch.mockResolvedValueOnce(makeDataSourceResponse({ id: "syn-2", basis: "SYNTHETIC" }));
    await useDataSourcesStore.getState().createSyntheticSource({
      projectId: "proj-1",
      name: "Synthetic B",
      protocol: "OPC UA",
      simulatorPort: 4840,
      config: { seed: null, variables: [] },
    });
    const body = JSON.parse((mockApiFetch.mock.calls[0][1] as { body: string }).body);
    expect(body).not.toHaveProperty("schemaFromSourceId");
  });
});

describe("runSynthetic / stopSynthetic (IS-145)", () => {
  it("runSynthetic POSTs run-synthetic, tracks the runId, and reloads", async () => {
    useDataSourcesStore.setState({ dataSources: [], currentProjectId: "proj-1", syntheticRunIds: {} });
    mockApiFetch
      .mockResolvedValueOnce({ runId: "run-9", state: "RUNNING" }) // run-synthetic
      .mockResolvedValueOnce({ items: [], nextCursor: null, limit: 50 }); // loadDataSources
    await useDataSourcesStore.getState().runSynthetic("syn-1", "proj-1");
    expect(mockApiFetch).toHaveBeenCalledWith(
      "/api/v1/projects/proj-1/data-sources/syn-1/run-synthetic",
      expect.objectContaining({ method: "POST" }),
    );
    expect(useDataSourcesStore.getState().syntheticRunIds["syn-1"]).toBe("run-9");
  });

  it("stopSynthetic stops the tracked run via /runs/{id}/stop and clears the mapping", async () => {
    useDataSourcesStore.setState({
      dataSources: [],
      currentProjectId: "proj-1",
      syntheticRunIds: { "syn-1": "run-9" },
    });
    mockApiFetch
      .mockResolvedValueOnce(undefined) // /runs/run-9/stop
      .mockResolvedValueOnce({ items: [], nextCursor: null, limit: 50 }); // loadDataSources
    await useDataSourcesStore.getState().stopSynthetic("syn-1", "proj-1");
    expect(mockApiFetch).toHaveBeenCalledWith(
      "/api/v1/projects/proj-1/runs/run-9/stop",
      expect.objectContaining({ method: "POST" }),
    );
    expect(useDataSourcesStore.getState().syntheticRunIds["syn-1"]).toBeUndefined();
  });

  it("stopSynthetic falls back to /data-sources/{id}/stop when no run id is tracked", async () => {
    useDataSourcesStore.setState({ dataSources: [], currentProjectId: "proj-1", syntheticRunIds: {} });
    mockApiFetch
      .mockResolvedValueOnce(undefined) // /data-sources/syn-1/stop
      .mockResolvedValueOnce({ items: [], nextCursor: null, limit: 50 });
    await useDataSourcesStore.getState().stopSynthetic("syn-1", "proj-1");
    expect(mockApiFetch).toHaveBeenCalledWith(
      "/api/v1/projects/proj-1/data-sources/syn-1/stop",
      expect.objectContaining({ method: "POST" }),
    );
  });
});

describe("mapDataSource (IS-127 field mapping)", () => {
  it("maps serveUrl to endpoint", async () => {
    mockApiFetch.mockResolvedValueOnce({
      items: [makeDataSourceResponse({ serveUrl: "opc.tcp://sim:4840" })],
      nextCursor: null,
      limit: 50,
    });
    await useDataSourcesStore.getState().loadDataSources("proj-1");
    expect(useDataSourcesStore.getState().dataSources[0].endpoint).toBe("opc.tcp://sim:4840");
  });

  it("maps basis from the response", async () => {
    mockApiFetch.mockResolvedValueOnce({
      items: [makeDataSourceResponse({ basis: "IMPORT" })],
      nextCursor: null,
      limit: 50,
    });
    await useDataSourcesStore.getState().loadDataSources("proj-1");
    expect(useDataSourcesStore.getState().dataSources[0].basis).toBe("IMPORT");
  });

  it("maps realDeviceEndpoint from the response (SCAN source)", async () => {
    mockApiFetch.mockResolvedValueOnce({
      items: [makeDataSourceResponse({ basis: "SCAN", realDeviceEndpoint: "opc.tcp://device:4840" })],
      nextCursor: null,
      limit: 50,
    });
    await useDataSourcesStore.getState().loadDataSources("proj-1");
    expect(useDataSourcesStore.getState().dataSources[0].realDeviceEndpoint).toBe("opc.tcp://device:4840");
  });

  it("maps null realDeviceEndpoint for non-SCAN sources", async () => {
    mockApiFetch.mockResolvedValueOnce({
      items: [makeDataSourceResponse({ basis: "IMPORT", realDeviceEndpoint: null })],
      nextCursor: null,
      limit: 50,
    });
    await useDataSourcesStore.getState().loadDataSources("proj-1");
    expect(useDataSourcesStore.getState().dataSources[0].realDeviceEndpoint).toBeNull();
  });

  it("maps simulatorPort from the response", async () => {
    mockApiFetch.mockResolvedValueOnce({
      items: [makeDataSourceResponse({ simulatorPort: 44818 })],
      nextCursor: null,
      limit: 50,
    });
    await useDataSourcesStore.getState().loadDataSources("proj-1");
    expect(useDataSourcesStore.getState().dataSources[0].simulatorPort).toBe(44818);
  });

  it("maps parameterCount from the response (IS-149)", async () => {
    mockApiFetch.mockResolvedValueOnce({
      items: [makeDataSourceResponse({ parameterCount: 2480 })],
      nextCursor: null,
      limit: 50,
    });
    await useDataSourcesStore.getState().loadDataSources("proj-1");
    expect(useDataSourcesStore.getState().dataSources[0].parameterCount).toBe(2480);
  });

  it("defaults parameterCount to 0 when missing from the response", async () => {
    mockApiFetch.mockResolvedValueOnce({
      items: [makeDataSourceResponse()],
      nextCursor: null,
      limit: 50,
    });
    await useDataSourcesStore.getState().loadDataSources("proj-1");
    expect(useDataSourcesStore.getState().dataSources[0].parameterCount).toBe(0);
  });
});
