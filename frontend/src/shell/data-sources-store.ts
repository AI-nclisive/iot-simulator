import { create } from "zustand";
import { apiFetch, ApiError, mapProtocol, mapRuntimeStateToStatus, mapRuntimeStateToHealth } from "../api";
import type { DataSourceRow } from "../surfaces/mock-data-sources";
import type { BackendProtocol, BackendRuntimeState, Page } from "../api";

// Backend response shape from GET/POST/PUT /api/v1/projects/{pid}/data-sources (IS-127 shape)
type DataSourceResponse = {
  id: string;
  projectId: string;
  name: string;
  protocol: BackendProtocol;
  basis: "SCAN" | "MANUAL" | "IMPORT" | "SYNTHETIC";
  schemaId: string | null;
  schemaVersion: number | null;
  simulatorPort: number;
  realDeviceEndpoint: string | null;
  serveUrl: string;
  runtimeConfig: string | null;
  enabled: boolean;
  runtimeState: BackendRuntimeState;
  credentialState: string;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
  version: number;
};

/** Kept for test-file compatibility — parses old JSON-wrapped endpoint values. */
export function parseEndpointUrl(raw: string | null): string {
  if (!raw) return "";
  try {
    const parsed = JSON.parse(raw);
    const url = parsed?.url;
    return typeof url === "string" && url.trim() ? url : "";
  } catch {
    return raw;
  }
}

function mapDataSource(d: DataSourceResponse): DataSourceRow {
  return {
    id: d.id,
    name: d.name,
    protocol: mapProtocol(d.protocol),
    basis: d.basis,
    simulatorPort: d.simulatorPort,
    realDeviceEndpoint: d.realDeviceEndpoint ?? null,
    endpoint: d.serveUrl ?? "",
    parameterCount: 0, // TODO(UI-097): no backend field — will come from schema node count
    status: mapRuntimeStateToStatus(d.runtimeState),
    health: mapRuntimeStateToHealth(d.runtimeState) ?? "Healthy",
  };
}

type CreateDataSourceInput = {
  endpoint: string;
  name: string;
  protocol: DataSourceRow["protocol"];
};

type UpdateSourceConfigInput = {
  name: string;
  simulatorPort?: number;
  realDeviceEndpoint?: string | null;
};

type DataSourcesState = {
  dataSources: DataSourceRow[];
  isLoading: boolean;
  error: string | null;
  loadDataSources: (projectId: string) => Promise<void>;
  createDataSource: (input: CreateDataSourceInput & { projectId: string }) => Promise<string>;
  deleteDataSource: (rowId: string, projectId?: string) => Promise<void>;
  duplicateDataSource: (rowId: string, projectId?: string) => Promise<void>;
  stopDataSource: (rowId: string, projectId?: string) => Promise<void>;
  updateSourceConfiguration: (
    rowId: string,
    input: UpdateSourceConfigInput,
    projectId?: string,
  ) => Promise<void>;
  currentProjectId: string;
};

function backendProtocol(protocol: DataSourceRow["protocol"]): BackendProtocol {
  return protocol === "OPC UA" ? "OPC_UA" : "MODBUS_TCP";
}

export const useDataSourcesStore = create<DataSourcesState>((set, get) => ({
  dataSources: [],
  isLoading: false,
  error: null,
  currentProjectId: "",

  loadDataSources: async (projectId: string) => {
    set({ isLoading: true, error: null, currentProjectId: projectId });
    try {
      const { items } = await apiFetch<Page<DataSourceResponse>>(
        `/api/v1/projects/${projectId}/data-sources`,
      );
      set({ dataSources: items.map(mapDataSource), isLoading: false });
    } catch (err) {
      const message = err instanceof ApiError ? err.title : "Failed to load data sources";
      set({ error: message, isLoading: false });
    }
  },

  createDataSource: async ({ endpoint, name, protocol, projectId }) => {
    const data = await apiFetch<DataSourceResponse>(
      `/api/v1/projects/${projectId}/data-sources`,
      {
        method: "POST",
        body: JSON.stringify({
          name,
          endpoint,
          protocol: backendProtocol(protocol),
          basis: "MANUAL",
        }),
      },
    );
    const row = mapDataSource(data);
    set((state) => ({ dataSources: [...state.dataSources, row] }));
    return row.id;
  },

  deleteDataSource: async (rowId, projectId) => {
    const pid = projectId ?? get().currentProjectId;
    await apiFetch<undefined>(
      `/api/v1/projects/${pid}/data-sources/${rowId}`,
      { method: "DELETE" },
    );
    set((state) => ({
      dataSources: state.dataSources.filter((row) => row.id !== rowId),
    }));
  },

  duplicateDataSource: async (rowId, projectId) => {
    const pid = projectId ?? get().currentProjectId;
    const data = await apiFetch<DataSourceResponse>(
      `/api/v1/projects/${pid}/data-sources/${rowId}/duplicate`,
      { method: "POST" },
    );
    const row = mapDataSource(data);
    set((state) => ({ dataSources: [...state.dataSources, row] }));
  },

  stopDataSource: async (rowId, projectId) => {
    const pid = projectId ?? get().currentProjectId;
    const data = await apiFetch<DataSourceResponse>(
      `/api/v1/projects/${pid}/data-sources/${rowId}/stop`,
      { method: "POST" },
    );
    const updated = mapDataSource(data);
    set((state) => ({
      dataSources: state.dataSources.map((row) => (row.id === rowId ? updated : row)),
    }));
  },

  updateSourceConfiguration: async (rowId, input, projectId) => {
    const pid = projectId ?? get().currentProjectId;
    const body: Record<string, unknown> = { name: input.name };
    if (input.simulatorPort !== undefined) body.simulatorPort = input.simulatorPort;
    if (input.realDeviceEndpoint !== undefined) body.realDeviceEndpoint = input.realDeviceEndpoint;
    const data = await apiFetch<DataSourceResponse>(
      `/api/v1/projects/${pid}/data-sources/${rowId}`,
      { method: "PUT", body: JSON.stringify(body) },
    );
    const updated = mapDataSource(data);
    set((state) => ({
      dataSources: state.dataSources.map((row) => (row.id === rowId ? updated : row)),
    }));
  },
}));

export type { CreateDataSourceInput };
export type { DataSourceRow } from "../surfaces/mock-data-sources";
