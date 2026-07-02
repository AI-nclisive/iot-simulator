/**
 * Tests for RecordingDetailPage (UI-115)
 *
 * Covers:
 * - Loading state renders while isLoading is true
 * - Error state renders when store has an error
 * - Not-found state renders when recording is absent
 * - Recording metadata renders when recording is found
 * - Schema tab is active by default
 * - Switching to Values tab renders value count
 * - Values tab shows empty state when valueCount is 0
 */

import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useArtifactsStore } from "../shell/artifacts-store";
import { RecordingDetailPage } from "./recording-detail-page";

const { mockNavigate } = vi.hoisted(() => ({ mockNavigate: vi.fn() }));

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock("../shell/shell-store", () => ({
  useShellStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ accessMode: "local", sharedRole: "admin", currentProjectId: "proj-1" }),
}));

vi.mock("../shell/artifacts-store", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../shell/artifacts-store")>();
  return { ...actual, useArtifactsStore: actual.useArtifactsStore };
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function renderWithId(recordingId: string) {
  render(
    <MemoryRouter initialEntries={[`/recordings/${recordingId}`]}>
      <Routes>
        <Route path="/recordings/:recordingId" element={<RecordingDetailPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

const baseArtifact = {
  id: "rec-001",
  sourceId: "src-abc",
  origin: "captured" as const,
  valueCount: 1500,
  createdAt: "2026-07-01T10:00:00Z",
  createdBy: "test-user",
};

beforeEach(() => {
  useArtifactsStore.setState({
    artifacts: [],
    samples: [],
    isLoading: false,
    isSamplesLoading: false,
    error: null,
    samplesError: null,
    loadRecordings: vi.fn().mockResolvedValue(undefined),
    loadRecordingById: vi.fn().mockResolvedValue(undefined),
    loadSamples: vi.fn().mockResolvedValue(undefined),
  });
});

describe("RecordingDetailPage — loading state", () => {
  it("shows loading panel while isLoading is true", () => {
    useArtifactsStore.setState({ isLoading: true });
    renderWithId("rec-001");
    expect(screen.getByText("Loading…")).toBeTruthy();
  });
});

describe("RecordingDetailPage — error state", () => {
  it("shows error panel when store has error", () => {
    useArtifactsStore.setState({ error: "Network failure" });
    renderWithId("rec-001");
    expect(screen.getByText("Could not load recording.")).toBeTruthy();
  });
});

describe("RecordingDetailPage — not found state", () => {
  it("shows not-found panel when recording is absent", () => {
    renderWithId("rec-unknown");
    expect(screen.getByText("Recording not found.")).toBeTruthy();
  });
});

describe("RecordingDetailPage — recording found", () => {
  beforeEach(() => {
    useArtifactsStore.setState({ artifacts: [baseArtifact] });
  });

  it("renders source ID in metadata", () => {
    renderWithId("rec-001");
    expect(screen.getByText("src-abc")).toBeTruthy();
  });

  it("renders captured-by in metadata", () => {
    renderWithId("rec-001");
    expect(screen.getByText("test-user")).toBeTruthy();
  });

  it("renders Recorded badge for captured origin", () => {
    renderWithId("rec-001");
    expect(screen.getByText("Recorded")).toBeTruthy();
  });

  it("Schema tab is active by default", () => {
    renderWithId("rec-001");
    expect(screen.getByText("Schema not yet available.")).toBeTruthy();
  });

  it("switches to Values tab and shows value count", async () => {
    renderWithId("rec-001");
    await userEvent.click(screen.getByRole("button", { name: "Values" }));
    expect(screen.getByText("Captured values")).toBeTruthy();
  });
});

describe("RecordingDetailPage — values tab empty state", () => {
  it("shows empty state on Values tab when valueCount is 0", async () => {
    useArtifactsStore.setState({ artifacts: [{ ...baseArtifact, valueCount: 0 }] });
    renderWithId("rec-001");
    await userEvent.click(screen.getByRole("button", { name: "Values" }));
    expect(screen.getByText("No values captured.")).toBeTruthy();
  });
});
