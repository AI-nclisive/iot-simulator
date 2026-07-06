/**
 * Tests for useArtifactsStore (UI-108 live-API + Samples)
 *
 * Mocks apiFetch.
 * Covers:
 * - loadRecordings: populates artifacts from API with origin mapping
 * - loadRecordings error path: sets error state
 * - loadSamples: populates samples from API
 * - loadSamples error path: sets samplesError
 * - createSample: POST and prepend to samples list
 * - deleteSample: DELETE and remove from samples list
 */

import { afterEach, beforeEach, describe, expect, it, vi, type MockedFunction } from "vitest";
import { useArtifactsStore } from "./artifacts-store";
import { apiFetch, ApiError } from "../api";

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

function makeRecordingResponse(overrides: Partial<{
  id: string;
  origin: string;
  dataSourceId: string;
}> = {}) {
  return {
    id: overrides.id ?? "rec-01",
    projectId: "proj-1",
    dataSourceId: overrides.dataSourceId ?? "src-01",
    schemaVersion: 1,
    origin: overrides.origin ?? "SCAN_RECORD",
    valueCount: 100,
    createdAt: "2026-06-30T10:00:00Z",
    createdBy: "Tester",
    version: 1,
  };
}

function makeSampleResponse(overrides: Partial<{
  id: string;
  name: string;
}> = {}) {
  return {
    id: overrides.id ?? "sample-01",
    projectId: "proj-1",
    derivedFromRecordingId: "rec-01",
    name: overrides.name ?? "Test Sample",
    selection: "0-100",
    tags: ["tag1"],
    createdAt: "2026-06-30T10:00:00Z",
    createdBy: "Tester",
    version: 1,
  };
}

beforeEach(() => {
  useArtifactsStore.setState({
    artifacts: [],
    samples: [],
    isLoading: false,
    isSamplesLoading: false,
    error: null,
    samplesError: null,
  });
  vi.clearAllMocks();
});

afterEach(() => {
  vi.clearAllMocks();
});

// ─── loadRecordings ───────────────────────────────────────────────────────────

describe("loadRecordings", () => {
  it("populates artifacts from API response", async () => {
    mockApiFetch.mockResolvedValueOnce({ items: [makeRecordingResponse()], nextCursor: null, limit: 50 });
    await useArtifactsStore.getState().loadRecordings("proj-1");
    const { artifacts } = useArtifactsStore.getState();
    expect(artifacts).toHaveLength(1);
    expect(artifacts[0].id).toBe("rec-01");
    expect(artifacts[0].sourceId).toBe("src-01");
  });

  it("maps SCAN_RECORD origin to captured", async () => {
    mockApiFetch.mockResolvedValueOnce({ items: [makeRecordingResponse({ origin: "SCAN_RECORD" })], nextCursor: null, limit: 50 });
    await useArtifactsStore.getState().loadRecordings("proj-1");
    expect(useArtifactsStore.getState().artifacts[0].origin).toBe("captured");
  });

  it("maps IMPORTED origin to imported", async () => {
    mockApiFetch.mockResolvedValueOnce({ items: [makeRecordingResponse({ origin: "IMPORTED" })], nextCursor: null, limit: 50 });
    await useArtifactsStore.getState().loadRecordings("proj-1");
    expect(useArtifactsStore.getState().artifacts[0].origin).toBe("imported");
  });

  it("sets error on API failure", async () => {
    mockApiFetch.mockRejectedValueOnce(
      new ApiError(500, "Server error", undefined, undefined),
    );
    await useArtifactsStore.getState().loadRecordings("proj-1");
    const { error, isLoading } = useArtifactsStore.getState();
    expect(error).toBe("Server error");
    expect(isLoading).toBe(false);
  });
});

// ─── loadSamples ──────────────────────────────────────────────────────────────

describe("loadSamples", () => {
  it("populates samples from API response (cursor-paginated format)", async () => {
    mockApiFetch.mockResolvedValueOnce({ items: [makeSampleResponse()], nextCursor: null });
    await useArtifactsStore.getState().loadSamples("proj-1");
    const { samples } = useArtifactsStore.getState();
    expect(samples).toHaveLength(1);
    expect(samples[0].id).toBe("sample-01");
    expect(samples[0].name).toBe("Test Sample");
  });

  it("sets samplesError on API failure", async () => {
    mockApiFetch.mockRejectedValueOnce(
      new ApiError(500, "Samples error", undefined, undefined),
    );
    await useArtifactsStore.getState().loadSamples("proj-1");
    const { samplesError, isSamplesLoading } = useArtifactsStore.getState();
    expect(samplesError).toBe("Samples error");
    expect(isSamplesLoading).toBe(false);
  });
});

// ─── createSample ─────────────────────────────────────────────────────────────

describe("createSample", () => {
  it("POST creates and prepends sample to list", async () => {
    const existing = makeSampleResponse({ id: "sample-existing", name: "Existing" });
    useArtifactsStore.setState({ samples: [existing] });

    const newSample = makeSampleResponse({ id: "sample-new", name: "New Sample" });
    mockApiFetch.mockResolvedValueOnce(newSample);

    const result = await useArtifactsStore.getState().createSample("proj-1", {
      name: "New Sample",
      derivedFromRecordingId: "rec-01",
      selection: "0-50",
      tags: [],
    });

    expect(result.id).toBe("sample-new");
    const { samples } = useArtifactsStore.getState();
    expect(samples).toHaveLength(2);
    expect(samples[0].id).toBe("sample-new");
    expect(samples[1].id).toBe("sample-existing");
  });
});

// ─── appendSample ─────────────────────────────────────────────────────────────

describe("appendSample", () => {
  it("prepends sample to existing list without an API call", () => {
    const existing = makeSampleResponse({ id: "sample-existing", name: "Existing" });
    useArtifactsStore.setState({ samples: [existing] });

    const newSample = makeSampleResponse({ id: "sample-new", name: "New" });
    useArtifactsStore.getState().appendSample(newSample);

    const { samples } = useArtifactsStore.getState();
    expect(samples).toHaveLength(2);
    expect(samples[0].id).toBe("sample-new");
    expect(samples[1].id).toBe("sample-existing");
    expect(mockApiFetch).not.toHaveBeenCalled();
  });
});

// ─── deleteSample ─────────────────────────────────────────────────────────────

describe("deleteSample", () => {
  it("DELETE removes sample from store", async () => {
    useArtifactsStore.setState({
      samples: [
        makeSampleResponse({ id: "sample-01" }),
        makeSampleResponse({ id: "sample-02", name: "Keep me" }),
      ],
    });
    mockApiFetch.mockResolvedValueOnce(undefined);

    await useArtifactsStore.getState().deleteSample("proj-1", "sample-01");
    const { samples } = useArtifactsStore.getState();
    expect(samples).toHaveLength(1);
    expect(samples[0].id).toBe("sample-02");
  });
});

describe("mapRecording — name field (UI-131)", () => {
  it("maps a present name from the API response", async () => {
    mockApiFetch.mockResolvedValueOnce({
      items: [{ ...makeRecordingResponse({ id: "rec-n1" }), name: "My capture" }],
      nextCursor: null, limit: 50,
    });
    await useArtifactsStore.getState().loadRecordings("proj-1");
    const artifact = useArtifactsStore.getState().artifacts.find((a) => a.id === "rec-n1");
    expect(artifact?.name).toBe("My capture");
  });

  it("maps undefined when name is absent in the API response", async () => {
    mockApiFetch.mockResolvedValueOnce({
      items: [makeRecordingResponse({ id: "rec-n2" })],
      nextCursor: null, limit: 50,
    });
    await useArtifactsStore.getState().loadRecordings("proj-1");
    const artifact = useArtifactsStore.getState().artifacts.find((a) => a.id === "rec-n2");
    expect(artifact?.name).toBeUndefined();
  });
});
