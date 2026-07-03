/**
 * Tests for RecordingDetailPage (UI-115, UI-119)
 *
 * Covers:
 * - Loading state while isLoading is true
 * - Error state when store has error
 * - Not-found state when recording is absent
 * - Recording metadata renders when recording is found
 * - Schema tab is active by default
 * - Values tab: empty state when valueCount = 0
 * - Values tab: renders table rows from API (UI-119)
 * - Values tab: shows "Load more" when nextCursor present
 * - Values tab: loads second page on "Load more" click
 * - Values tab: shows all-loaded message when no nextCursor
 */

import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useArtifactsStore } from "../shell/artifacts-store";
import { RecordingDetailPage } from "./recording-detail-page";

const { mockNavigate } = vi.hoisted(() => ({ mockNavigate: vi.fn() }));
const { mockApiFetch } = vi.hoisted(() => ({ mockApiFetch: vi.fn() }));

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock("../shell/shell-store", () => ({
  useShellStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ accessMode: "local", sharedRole: "admin", currentProjectId: "proj-1" }),
}));

vi.mock("../api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../api")>();
  return { ...actual, apiFetch: mockApiFetch };
});

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

const sampleValues = [
  { parameterId: "ns=2;s=Temp", parameterPath: "/Root/Temp", timestamp: "2026-01-01T00:00:00Z", value: "21.5", quality: "GOOD" },
  { parameterId: "ns=2;s=Press", parameterPath: "/Root/Press", timestamp: "2026-01-01T00:00:01Z", value: "1013", quality: "GOOD" },
];

beforeEach(() => {
  mockApiFetch.mockResolvedValue({ items: [], nextCursor: null, total: 0 });
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
});

describe("RecordingDetailPage — values tab empty state", () => {
  it("shows empty state on Values tab when valueCount is 0", async () => {
    useArtifactsStore.setState({ artifacts: [{ ...baseArtifact, valueCount: 0 }] });
    renderWithId("rec-001");
    await userEvent.click(screen.getByRole("button", { name: "Values" }));
    expect(screen.getByText("No values captured.")).toBeTruthy();
  });
});

describe("RecordingDetailPage — values tab (UI-119)", () => {
  beforeEach(() => {
    useArtifactsStore.setState({ artifacts: [baseArtifact] });
  });

  it("renders value rows in table after API call", async () => {
    mockApiFetch.mockResolvedValueOnce({ items: sampleValues, nextCursor: null, total: 2 });
    renderWithId("rec-001");
    await userEvent.click(screen.getByRole("button", { name: "Values" }));
    await waitFor(() => expect(screen.getByText("/Root/Temp")).toBeTruthy());
    expect(screen.getByText("/Root/Press")).toBeTruthy();
    expect(screen.getByText("21.5")).toBeTruthy();
  });

  it("shows Load more button when nextCursor is present", async () => {
    mockApiFetch.mockResolvedValueOnce({ items: sampleValues, nextCursor: "abc123", total: 3 });
    renderWithId("rec-001");
    await userEvent.click(screen.getByRole("button", { name: "Values" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Load more" })).toBeTruthy());
  });

  it("loads second page when Load more is clicked", async () => {
    const page2 = [
      { parameterId: "ns=2;s=Flow", timestamp: "2026-01-01T00:00:02Z", value: "5.0", quality: "GOOD" },
    ];
    mockApiFetch
      .mockResolvedValueOnce({ items: sampleValues, nextCursor: "abc123", total: 3 })
      .mockResolvedValueOnce({ items: page2, nextCursor: null, total: 3 });

    renderWithId("rec-001");
    await userEvent.click(screen.getByRole("button", { name: "Values" }));
    await waitFor(() => screen.getByRole("button", { name: "Load more" }));
    await userEvent.click(screen.getByRole("button", { name: "Load more" }));
    await waitFor(() => expect(screen.getByText("ns=2;s=Flow")).toBeTruthy());
    expect(screen.queryByRole("button", { name: "Load more" })).toBeNull();
  });

  it("shows all-loaded message when all values fetched", async () => {
    mockApiFetch.mockResolvedValueOnce({ items: sampleValues, nextCursor: null, total: 2 });
    renderWithId("rec-001");
    await userEvent.click(screen.getByRole("button", { name: "Values" }));
    await waitFor(() =>
      expect(screen.getByText(/All.*values loaded/)).toBeTruthy(),
    );
  });

  it("shows error panel when initial API call fails (no rows loaded)", async () => {
    const { ApiError } = await import("../api");
    mockApiFetch.mockRejectedValueOnce(new ApiError(500, "Server error", "Internal failure", undefined));
    renderWithId("rec-001");
    await userEvent.click(screen.getByRole("button", { name: "Values" }));
    await waitFor(() => expect(screen.getByText("Failed to load values.")).toBeTruthy());
  });

  it("keeps rows visible and shows inline error when Load more fails", async () => {
    const { ApiError } = await import("../api");
    mockApiFetch
      .mockResolvedValueOnce({ items: sampleValues, nextCursor: "abc123", total: 3 })
      .mockRejectedValueOnce(new ApiError(500, "Server error", "Internal failure", undefined));

    renderWithId("rec-001");
    await userEvent.click(screen.getByRole("button", { name: "Values" }));
    await waitFor(() => screen.getByRole("button", { name: "Load more" }));
    await userEvent.click(screen.getByRole("button", { name: "Load more" }));

    await waitFor(() => expect(screen.getByText("Server error")).toBeTruthy());
    expect(screen.getByText("/Root/Temp")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Load more" })).toBeTruthy();
    expect(screen.queryByText("Failed to load values.")).toBeNull();
  });
});
