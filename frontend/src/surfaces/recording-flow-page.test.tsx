/**
 * Tests for RecordingFlowPage (UI-112)
 *
 * Covers:
 * - Ready state: "Start recording" button shown, not disabled
 * - useLiveValues called with correct sourceId and enabled=false when not capturing
 * - SSE status "open" + rows > 0 → recordingState shows "Recording"
 * - SSE status "stale" → recordingState shows "Disconnected"
 * - Value count (MetricCard) shows liveRows.length during capture
 * - No fake increment: count stays 0 if SSE delivers no rows while capturing
 * - Stop recording: calls POST recording/stop, shows final valueCount from response
 */

import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { LiveValuesResult } from "../shell/use-live-values";
import type { SourceValueRow } from "../surfaces/mock-source-values";

// ---------------------------------------------------------------------------
// Hoisted mock factories — declared before any vi.mock calls
// ---------------------------------------------------------------------------

const { mockUseLiveValues, mockApiFetch, mockNavigate, mockPush, mockAppendRecording } =
  vi.hoisted(() => ({
    mockUseLiveValues: vi.fn((): LiveValuesResult => ({ rows: [], status: "connecting" })),
    mockApiFetch: vi.fn(),
    mockNavigate: vi.fn(),
    mockPush: vi.fn(),
    mockAppendRecording: vi.fn(),
  }));

// ---------------------------------------------------------------------------
// Module mocks
// ---------------------------------------------------------------------------

vi.mock("../shell/use-live-values", () => ({
  useLiveValues: mockUseLiveValues,
}));

vi.mock("../api", () => ({
  apiFetch: mockApiFetch,
  ApiError: class ApiError extends Error {
    constructor(message: string, public readonly status: number, public readonly detail?: string) {
      super(message);
      this.name = "ApiError";
    }
  },
}));

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return { ...actual, useNavigate: () => mockNavigate };
});

// accessMode must be "local" | "shared" — use "local" so canRecordSource = true
vi.mock("../shell/shell-store", () => ({
  useShellStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({
      currentProjectId: "proj-1",
      accessMode: "local",
      sharedRole: "admin",
    }),
}));

vi.mock("../shell/data-sources-store", () => ({
  useDataSourcesStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({
      dataSources: [
        {
          id: "src-1",
          name: "Line A telemetry",
          protocol: "OPC UA",
          endpoint: "opc.tcp://line-a.local:4840",
          realDeviceEndpoint: "opc.tcp://line-a.local:4840",
          parameterCount: 2480,
        },
      ],
    }),
}));

vi.mock("../shell/artifacts-store", () => ({
  useArtifactsStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ appendRecording: mockAppendRecording }),
}));

vi.mock("../shell/notification-store", () => ({
  useNotificationStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ push: mockPush }),
}));

// ---------------------------------------------------------------------------
// Imports after mocks
// ---------------------------------------------------------------------------

import { RecordingFlowPage } from "./recording-flow-page";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function makeRow(overrides: Partial<SourceValueRow> = {}): SourceValueRow {
  return {
    id: "src-1:ns=2;s=Tag1",
    sourceId: "src-1",
    path: "ns=2;s=Tag1",
    dataType: "string",
    currentValue: "42",
    updatedAt: "10:00:01",
    freshness: "Live",
    pinned: false,
    ...overrides,
  };
}

const startResponse = {
  id: "rec-1",
  projectId: "proj-1",
  dataSourceId: "src-1",
  schemaVersion: 1,
  origin: "captured",
  valueCount: 0,
  createdAt: "2026-07-01T10:00:00Z",
  createdBy: "user",
  version: 1,
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/data-sources/src-1/record"]}>
      <Routes>
        <Route path="data-sources/:sourceId/record" element={<RecordingFlowPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("RecordingFlowPage — ready state", () => {
  it("shows Start recording button that is not disabled", () => {
    mockUseLiveValues.mockReturnValue({ rows: [], status: "connecting" });
    renderPage();

    const btn = screen.getByRole("button", { name: "Start recording" }) as HTMLButtonElement;
    expect(btn).toBeTruthy();
    expect(btn.disabled).toBe(false);
  });

  it("does not show Stop recording in initial ready state", () => {
    mockUseLiveValues.mockReturnValue({ rows: [], status: "connecting" });
    renderPage();

    expect(screen.queryByRole("button", { name: "Stop recording" })).toBeNull();
  });
});

describe("RecordingFlowPage — useLiveValues wiring", () => {
  it("calls useLiveValues with the correct sourceId", () => {
    mockUseLiveValues.mockReturnValue({ rows: [], status: "connecting" });
    renderPage();

    // sourceId param is "src-1" and captureActive starts false
    expect(mockUseLiveValues).toHaveBeenCalledWith("src-1", false);
  });

});

describe("RecordingFlowPage — SSE status open → Recording state", () => {
  it("shows Recording badge when SSE status is open and rows are present", async () => {
    const user = userEvent.setup();

    // Return open + rows from the very first render so state transitions fire
    // after Start is clicked (captureActive flips to true via no-values-yet state)
    mockUseLiveValues.mockReturnValue({ rows: [makeRow()], status: "open" });
    mockApiFetch.mockResolvedValue(startResponse);

    renderPage();
    await user.click(screen.getByRole("button", { name: "Start recording" }));

    await waitFor(() => {
      // Both the StatusBadge and the MetricCard "Capture state" should say "Recording"
      const allText = document.body.textContent ?? "";
      expect(allText).toContain("Recording");
    });
  });
});

describe("RecordingFlowPage — SSE status stale → Disconnected state", () => {
  it("shows Disconnected badge when SSE status is stale during capture", async () => {
    const user = userEvent.setup();

    mockUseLiveValues.mockReturnValue({ rows: [makeRow()], status: "stale" });
    mockApiFetch.mockResolvedValue(startResponse);

    renderPage();
    await user.click(screen.getByRole("button", { name: "Start recording" }));

    await waitFor(() => {
      const allText = document.body.textContent ?? "";
      expect(allText).toContain("Disconnected");
    });
  });
});

describe("RecordingFlowPage — value count synced from SSE rows", () => {
  it("shows liveRows.length as value count during capture", async () => {
    const user = userEvent.setup();

    mockUseLiveValues.mockReturnValue({
      rows: [
        makeRow({ id: "src-1:t1", path: "t1" }),
        makeRow({ id: "src-1:t2", path: "t2" }),
      ],
      status: "open",
    });
    mockApiFetch.mockResolvedValue(startResponse);

    renderPage();
    await user.click(screen.getByRole("button", { name: "Start recording" }));

    await waitFor(() => {
      // MetricCard "Captured values" shows "2" (liveRows.length)
      expect(screen.getByText("2")).toBeTruthy();
    });
  });

  it("shows 0 if SSE delivers no rows while capturing (no fake increment)", async () => {
    const user = userEvent.setup();

    // SSE connected but no rows
    mockUseLiveValues.mockReturnValue({ rows: [], status: "open" });
    mockApiFetch.mockResolvedValue(startResponse);

    renderPage();
    await user.click(screen.getByRole("button", { name: "Start recording" }));

    // Wait for the recording state to settle (should show Recording via SSE open)
    await waitFor(() => {
      const allText = document.body.textContent ?? "";
      expect(allText).toContain("Recording");
    });

    // "Captured values" MetricCard must show 0, not some fake-incremented number
    expect(screen.getByText("0")).toBeTruthy();
  });
});

describe("RecordingFlowPage — stop recording", () => {
  it("calls POST recording/stop and shows final valueCount from response", async () => {
    const user = userEvent.setup();

    mockUseLiveValues.mockReturnValue({ rows: [], status: "open" });

    mockApiFetch
      .mockResolvedValueOnce(startResponse)
      .mockResolvedValueOnce({ ...startResponse, valueCount: 42 });

    renderPage();
    await user.click(screen.getByRole("button", { name: "Start recording" }));

    // Wait for Stop button to appear (captureActive=true)
    const stopBtn = await screen.findByRole("button", { name: "Stop recording" });
    await user.click(stopBtn);

    await waitFor(() => {
      // Final valueCount=42 from the stop response appears at least once in the page
      // (both in the MetricCard and in the save-ready Recording summary panel)
      const matches = screen.getAllByText("42");
      expect(matches.length).toBeGreaterThan(0);
    });

    // Verify the stop endpoint was called
    expect(mockApiFetch).toHaveBeenCalledWith(
      "/api/v1/projects/proj-1/data-sources/src-1/recording/stop",
      { method: "POST" },
    );
  });
});
