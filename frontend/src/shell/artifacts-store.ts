import { create } from "zustand";
import { apiFetch, ApiError, type Page } from "../api";
import type { ReusableArtifact } from "../surfaces/mock-artifacts";

// Backend response shape from GET/POST /api/v1/projects/{pid}/recordings
type RecordingResponse = {
  id: string;
  projectId: string;
  dataSourceId: string;
  schemaVersion: number;
  origin: string;
  name?: string;
  valueCount: number;
  createdAt: string;
  createdBy: string;
  version: number;
};

function mapRecording(r: RecordingResponse): ReusableArtifact {
  return {
    id: r.id,
    name: r.name,
    createdAt: r.createdAt,
    createdBy: r.createdBy,
    sourceId: r.dataSourceId,
    origin: r.origin === "IMPORTED" ? "imported" : "captured",
    valueCount: r.valueCount,
  };
}

// Backend response shape from GET/POST /api/v1/projects/{pid}/samples
export type SampleResponse = {
  id: string;
  projectId: string;
  derivedFromRecordingId: string;
  name: string;
  selection: string;
  tags: string[];
  createdAt: string;
  createdBy: string;
  version: number;
};

export type CreateSampleRequest = {
  name: string;
  derivedFromRecordingId: string;
  selection: string;
  tags: string[];
};

type CreateRecordingInput = {
  createdBy: string;
  sourceId?: string;
  valueCount: number;
};

type ArtifactsState = {
  artifacts: ReusableArtifact[];
  samples: SampleResponse[];
  isLoading: boolean;
  isSamplesLoading: boolean;
  error: string | null;
  samplesError: string | null;
  loadRecordings: (projectId: string) => Promise<void>;
  loadRecordingById: (projectId: string, recordingId: string) => Promise<void>;
  loadSamples: (projectId: string) => Promise<void>;
  createSample: (projectId: string, req: CreateSampleRequest) => Promise<SampleResponse>;
  deleteSample: (projectId: string, sampleId: string) => Promise<void>;
  appendSample: (sample: SampleResponse) => void;
  // createRecording is kept for backward-compat with RecordingFlowPage
  // which uses start/stop capture endpoints separately; recording IDs are
  // appended to the store after a successful stop.
  createRecording: (input: CreateRecordingInput) => string;
  appendRecording: (artifact: ReusableArtifact) => void;
};

export const useArtifactsStore = create<ArtifactsState>((set, get) => ({
  artifacts: [],
  samples: [],
  isLoading: false,
  isSamplesLoading: false,
  error: null,
  samplesError: null,

  loadRecordings: async (projectId: string) => {
    set({ isLoading: true, error: null });
    try {
      const { items } = await apiFetch<Page<RecordingResponse>>(
        `/api/v1/projects/${projectId}/recordings`,
      );
      set({ artifacts: items.map(mapRecording), isLoading: false });
    } catch (err) {
      const message = err instanceof ApiError ? err.title : "Failed to load recordings";
      set({ error: message, isLoading: false });
    }
  },

  loadRecordingById: async (projectId: string, recordingId: string) => {
    if (get().artifacts.find((a) => a.id === recordingId)) return;
    set({ isLoading: true, error: null });
    try {
      const r = await apiFetch<RecordingResponse>(
        `/api/v1/projects/${projectId}/recordings/${recordingId}`,
      );
      set((state) => ({
        artifacts: [mapRecording(r), ...state.artifacts.filter((a) => a.id !== r.id)],
        isLoading: false,
      }));
    } catch (err) {
      const message = err instanceof ApiError ? err.title : "Failed to load recording";
      set({ error: message, isLoading: false });
    }
  },

  loadSamples: async (projectId: string) => {
    set({ isSamplesLoading: true, samplesError: null });
    try {
      const data = await apiFetch<{ items: SampleResponse[]; nextCursor?: string }>(
        `/api/v1/projects/${projectId}/samples`,
      );
      set({ samples: data.items, isSamplesLoading: false });
    } catch (err) {
      const message = err instanceof ApiError ? err.title : "Failed to load samples";
      set({ samplesError: message, isSamplesLoading: false });
    }
  },

  createSample: async (projectId: string, req: CreateSampleRequest) => {
    const data = await apiFetch<SampleResponse>(
      `/api/v1/projects/${projectId}/samples`,
      { method: "POST", body: JSON.stringify(req) },
    );
    set((state) => ({ samples: [data, ...state.samples] }));
    return data;
  },

  deleteSample: async (projectId: string, sampleId: string) => {
    await apiFetch<void>(
      `/api/v1/projects/${projectId}/samples/${sampleId}`,
      { method: "DELETE" },
    );
    set((state) => ({ samples: state.samples.filter((s) => s.id !== sampleId) }));
  },

  appendRecording: (artifact) => {
    set((state) => ({ artifacts: [artifact, ...state.artifacts] }));
  },

  appendSample: (sample) => {
    set((state) => ({ samples: [sample, ...state.samples] }));
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
