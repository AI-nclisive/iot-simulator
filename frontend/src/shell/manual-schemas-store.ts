import { create } from "zustand";
import { apiFetch, ApiError, type Page } from "../api";
import type { NodeDto } from "../surfaces/data-source-schema-editor";

// Backend response shape from GET/POST/PUT /api/v1/projects/{pid}/manual-schemas
export type ManualSchemaResponse = {
  id: string;
  projectId: string;
  protocol: string;
  name: string;
  description: string | null;
  nodes: NodeDto[];
  version: number;
};

export type CreateManualSchemaInput = {
  protocol: string;
  name: string;
  description?: string | null;
  nodes?: NodeDto[];
};

type ManualSchemasState = {
  schemas: ManualSchemaResponse[];
  isLoading: boolean;
  error: string | null;
  loadManualSchemas: (projectId: string) => Promise<void>;
  createManualSchema: (
    projectId: string,
    input: CreateManualSchemaInput,
  ) => Promise<ManualSchemaResponse>;
  duplicateManualSchema: (
    projectId: string,
    id: string,
    newName: string,
  ) => Promise<ManualSchemaResponse>;
  deleteManualSchema: (projectId: string, id: string) => Promise<void>;
};

export const useManualSchemasStore = create<ManualSchemasState>((set) => ({
  schemas: [],
  isLoading: false,
  error: null,

  loadManualSchemas: async (projectId: string) => {
    set({ isLoading: true, error: null });
    try {
      const { items } = await apiFetch<Page<ManualSchemaResponse>>(
        `/api/v1/projects/${projectId}/manual-schemas`,
      );
      set({ schemas: items, isLoading: false });
    } catch (err) {
      const message = err instanceof ApiError ? err.title : "Failed to load manual schemas";
      set({ error: message, isLoading: false });
    }
  },

  createManualSchema: async (projectId, input) => {
    const schema = await apiFetch<ManualSchemaResponse>(
      `/api/v1/projects/${projectId}/manual-schemas`,
      {
        method: "POST",
        body: JSON.stringify({
          protocol: input.protocol,
          name: input.name,
          description: input.description ?? null,
          nodes: input.nodes ?? [],
        }),
      },
    );
    set((state) => ({ schemas: [schema, ...state.schemas] }));
    return schema;
  },

  duplicateManualSchema: async (projectId, id, newName) => {
    const schema = await apiFetch<ManualSchemaResponse>(
      `/api/v1/projects/${projectId}/manual-schemas/${id}/duplicate`,
      { method: "POST", body: JSON.stringify({ name: newName }) },
    );
    set((state) => ({ schemas: [schema, ...state.schemas] }));
    return schema;
  },

  deleteManualSchema: async (projectId, id) => {
    await apiFetch<void>(`/api/v1/projects/${projectId}/manual-schemas/${id}`, {
      method: "DELETE",
    });
    set((state) => ({ schemas: state.schemas.filter((s) => s.id !== id) }));
  },
}));
