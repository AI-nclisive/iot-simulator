import { create } from "zustand";
import { apiFetch, ApiError, mapProtocol, mapRuntimeStateToStatus, mapRuntimeStateToHealth } from "../api";
import type { DataSourceRow } from "../surfaces/mock-data-sources";
import type { TypeResolutionEntry } from "../surfaces/create-data-source-wizard-page";
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
  /** IS-149: count of VARIABLE schema nodes */
  parameterCount?: number;
};


function mapDataSource(d: DataSourceResponse): DataSourceRow {
  return {
    id: d.id,
    name: d.name,
    protocol: mapProtocol(d.protocol),
    basis: d.basis,
    simulatorPort: d.simulatorPort,
    realDeviceEndpoint: d.realDeviceEndpoint ?? null,
    runtimeConfig: d.runtimeConfig ?? null,
    endpoint: d.serveUrl ?? "",
    parameterCount: d.parameterCount ?? 0, // IS-149: VARIABLE node count from backend
    status: mapRuntimeStateToStatus(d.runtimeState),
    health: mapRuntimeStateToHealth(d.runtimeState) ?? "Healthy",
  };
}

type CreateDataSourceInput = {
  endpoint: string;
  name: string;
  protocol: DataSourceRow["protocol"];
};

// ── Synthetic authoring (IS-145) — FE mirror of the backend SyntheticConfig ──────────────
// Serialized shape of domain/synthetic/PatternSpec.java (only fields relevant to `type` are set).
export type SyntheticPatternSpec = {
  type: "CONSTANT" | "RAMP" | "SINE" | "SQUARE" | "RANDOM_UNIFORM" | "RANDOM_WALK";
  value?: number;
  /** IS-168: a CONSTANT for a string-shaped structural/identifier type (GUID, NODE_ID, ...). */
  stringValue?: string;
  /** IS-168: a CONSTANT for BYTES, standard Base64. */
  bytesValueBase64?: string;
  min?: number;
  max?: number;
  periodMs?: number;
  volatility?: number;
};

export type SyntheticVariableConfig = {
  nodeId: string;
  dataType: string;
  pattern: SyntheticPatternSpec;
  updateRateMs: number;
};

export type SyntheticConfig = {
  seed: number | null;
  variables: SyntheticVariableConfig[];
};

type CreateSyntheticInput = {
  projectId: string;
  name: string;
  protocol: DataSourceRow["protocol"];
  simulatorPort: number;
  config: SyntheticConfig;
  /** Reuse an existing source's schema verbatim (IS-145); omit to derive from the config. */
  schemaFromSourceId?: string | null;
  /** Reuse a standalone Manual Schema's nodes verbatim (IS-173); mutually exclusive with schemaFromSourceId. */
  manualSchemaId?: string | null;
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
  createSyntheticSource: (input: CreateSyntheticInput) => Promise<string>;
  runSynthetic: (rowId: string, projectId?: string) => Promise<void>;
  stopSynthetic: (rowId: string, projectId?: string) => Promise<void>;
  deleteDataSource: (rowId: string, projectId?: string) => Promise<void>;
  duplicateDataSource: (rowId: string, projectId?: string) => Promise<void>;
  stopDataSource: (rowId: string, projectId?: string) => Promise<void>;
  /** Applies a completed rescan job (POST /{id}/rescan/{jobId}/apply) as a new schema version. */
  applyRescan: (
    rowId: string,
    jobId: string,
    typeResolutions: TypeResolutionEntry[],
    projectId?: string,
  ) => Promise<void>;
  updateSourceConfiguration: (
    rowId: string,
    input: UpdateSourceConfigInput,
    projectId?: string,
  ) => Promise<void>;
  /** Live synthetic run id per source (IS-145), used to stop via POST /runs/{id}/stop. */
  syntheticRunIds: Record<string, string>;
  currentProjectId: string;
};

function backendProtocol(protocol: DataSourceRow["protocol"]): BackendProtocol {
  return protocol === "OPC UA" ? "OPC_UA" : "MODBUS_TCP";
}

export const useDataSourcesStore = create<DataSourcesState>((set, get) => ({
  dataSources: [],
  isLoading: false,
  error: null,
  syntheticRunIds: {},
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

  createSyntheticSource: async ({
    projectId,
    name,
    protocol,
    simulatorPort,
    config,
    schemaFromSourceId,
    manualSchemaId,
  }) => {
    const data = await apiFetch<DataSourceResponse>(
      `/api/v1/projects/${projectId}/data-sources/synthetic`,
      {
        method: "POST",
        body: JSON.stringify({
          name,
          protocol: backendProtocol(protocol),
          simulatorPort,
          config,
          ...(schemaFromSourceId ? { schemaFromSourceId } : {}),
          ...(manualSchemaId ? { manualSchemaId } : {}),
        }),
      },
    );
    const row = mapDataSource(data);
    set((state) => ({ dataSources: [...state.dataSources, row] }));
    return row.id;
  },

  runSynthetic: async (rowId, projectId) => {
    const pid = projectId ?? get().currentProjectId;
    const run = await apiFetch<{ runId: string; state: string }>(
      `/api/v1/projects/${pid}/data-sources/${rowId}/run-synthetic`,
      { method: "POST", body: JSON.stringify({}) },
    );
    set((state) => ({
      syntheticRunIds: { ...state.syntheticRunIds, [rowId]: run.runId },
    }));
    // Reload so the row's status reflects the now-RUNNING source (drives the live values tab).
    await get().loadDataSources(pid);
  },

  stopSynthetic: async (rowId, projectId) => {
    const pid = projectId ?? get().currentProjectId;
    const runId = get().syntheticRunIds[rowId];
    if (runId) {
      await apiFetch<unknown>(`/api/v1/projects/${pid}/runs/${runId}/stop`, { method: "POST" });
    } else {
      // No tracked run id (e.g. after a reload) — fall back to tearing down the source.
      await apiFetch<unknown>(`/api/v1/projects/${pid}/data-sources/${rowId}/stop`, { method: "POST" });
    }
    set((state) => {
      const next = { ...state.syntheticRunIds };
      delete next[rowId];
      return { syntheticRunIds: next };
    });
    await get().loadDataSources(pid);
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

  applyRescan: async (rowId, jobId, typeResolutions, projectId) => {
    const pid = projectId ?? get().currentProjectId;
    const data = await apiFetch<DataSourceResponse>(
      `/api/v1/projects/${pid}/data-sources/${rowId}/rescan/${jobId}/apply`,
      { method: "POST", body: JSON.stringify({ typeResolutions }) },
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
