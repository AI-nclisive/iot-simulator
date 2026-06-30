import { create } from "zustand";
import { apiFetch, ApiError } from "../api";
import type { ReusableArtifact } from "../surfaces/mock-artifacts";

// Backend response shape from GET/POST /api/v1/projects/{pid}/recordings
type RecordingResponse = {
  id: string;
  projectId: string;
  dataSourceId: string;
  schemaVersion: number;
  origin: string;
  valueCount: number;
  createdAt: string;
  createdBy: string;
  version: number;
};

function mapRecording(r: RecordingResponse): ReusableArtifact {
  return {
    id: r.id,
    createdAt: r.createdAt,
    createdBy: r.createdBy,
    sourceId: r.dataSourceId,
    valueCount: r.valueCount,
  };
}

type CreateRecordingInput = {
  createdBy: string;
  sourceId?: string;
  valueCount: number;
};

type ArtifactsState = {
  artifacts: ReusableArtifact[];
  isLoading: boolean;
  error: string | null;
  loadRecordings: (projectId: string) => Promise<void>;
  // createRecording is kept for backward-compat with RecordingFlowPage
  // which uses start/stop capture endpoints separately; recording IDs are
  // appended to the store after a successful stop.
  createRecording: (input: CreateRecordingInput) => string;
  appendRecording: (artifact: ReusableArtifact) => void;
};

export const useArtifactsStore = create<ArtifactsState>((set) => ({
  artifacts: [],
  isLoading: false,
  error: null,

  loadRecordings: async (projectId: string) => {
    set({ isLoading: true, error: null });
    try {
      const data = await apiFetch<RecordingResponse[]>(
        `/api/v1/projects/${projectId}/recordings`,
      );
      set({ artifacts: data.map(mapRecording), isLoading: false });
    } catch (err) {
      const message = err instanceof ApiError ? err.title : "Failed to load recordings";
      set({ error: message, isLoading: false });
    }
  },

  appendRecording: (artifact) => {
    set((state) => ({ artifacts: [artifact, ...state.artifacts] }));
  },

  // Legacy sync helper — kept so existing call-sites compile.
  // For live capture, use the start/stop endpoints in RecordingFlowPage instead.
  createRecording: ({ createdBy, sourceId, valueCount }) => {
    // TODO(UI-097): This is a local-only fallback; real recordings are persisted
    // via POST .../recording/start + stop. Call appendRecording after a successful stop.
    const id = `local-${Date.now()}`;
    const artifact: ReusableArtifact = {
      id,
      createdAt: new Date().toISOString(),
      createdBy,
      sourceId,
      valueCount,
    };
    set((state) => ({ artifacts: [artifact, ...state.artifacts] }));
    return id;
  },
}));

export type { CreateRecordingInput };
