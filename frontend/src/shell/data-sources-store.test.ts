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

function makeDataSourceResponse(overrides: Partial<{ id: string; name: string; runtimeState: string }> = {}) {
  return {
    id: overrides.id ?? "src-01",
    projectId: "proj-1",
    name: overrides.name ?? "Line A",
    protocol: "OPC_UA" as const,
    basis: "SCAN" as const,
    schemaId: null,
    schemaVersion: null,
    endpoint: "opc.tcp://test:4840",
    runtimeConfig: null,
    enabled: true,
    runtimeState: (overrides.runtimeState ?? "STOPPED") as "STOPPED",
    credentialState: "NONE",
    createdAt: "2026-06-01T00:00:00Z",
    updatedAt: "2026-06-01T00:00:00Z",
    createdBy: "testuser",
    version: 1,
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
    mockApiFetch.mockResolvedValueOnce([
      makeDataSourceResponse({ id: "src-01", name: "Source A" }),
      makeDataSourceResponse({ id: "src-02", name: "Source B" }),
    ]);
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
    mockApiFetch.mockResolvedValueOnce([]);
    await useDataSourcesStore.getState().loadDataSources("proj-42");
    expect(useDataSourcesStore.getState().currentProjectId).toBe("proj-42");
  });
});

describe("deleteDataSource", () => {
  it("removes the row from dataSources after DELETE", async () => {
    useDataSourcesStore.setState({
      dataSources: [
        { id: "src-01", name: "Source A", protocol: "OPC UA", endpoint: "opc.tcp://test:4840", parameterCount: 0, status: "Stopped", health: "Healthy" },
      ],
      currentProjectId: "proj-1",
    });
    mockApiFetch.mockResolvedValueOnce(undefined);
    await useDataSourcesStore.getState().deleteDataSource("src-01", "proj-1");
    expect(useDataSourcesStore.getState().dataSources).toHaveLength(0);
  });
});

describe("startDataSource", () => {
  it("updates the row status from API response", async () => {
    useDataSourcesStore.setState({
      dataSources: [
        { id: "src-01", name: "Source A", protocol: "OPC UA", endpoint: "opc.tcp://test:4840", parameterCount: 0, status: "Stopped", health: "Healthy" },
      ],
      currentProjectId: "proj-1",
    });
    mockApiFetch.mockResolvedValueOnce(makeDataSourceResponse({ id: "src-01", runtimeState: "RUNNING" }));
    await useDataSourcesStore.getState().startDataSource("src-01", "proj-1");
    const row = useDataSourcesStore.getState().dataSources[0];
    expect(row.status).toBe("Active");
  });
});

describe("duplicateDataSource", () => {
  it("calls POST /data-sources/{rowId}/duplicate and appends the copy", async () => {
    useDataSourcesStore.setState({
      dataSources: [
        { id: "src-01", name: "Source A", protocol: "OPC UA", endpoint: "opc.tcp://test:4840", parameterCount: 0, status: "Stopped", health: "Healthy" },
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
