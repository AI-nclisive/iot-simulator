/**
 * Tests for ReplayFlowPage (UI-126)
 *
 * Covers:
 * - Completion detection: run disappears from active-runs poll → UI transitions to "completed"
 *   (the most non-obvious logic: runSeenRef set at POST-time, next empty poll drives completion)
 */

import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import type { ActiveRunResponse } from "../shell/use-active-runs";
import { ApiError } from "../api";

const SOURCE_ID = "src-1";
const PROJECT_ID = "proj-1";
const ARTIFACT_ID = "art-1";
const RUN_ID = "run-99";

// ── hoisted mocks ─────────────────────────────────────────────────────────────

const { mockUseActiveRuns, mockApiFetch, mockLoadDataSources } = vi.hoisted(() => ({
  mockUseActiveRuns: vi.fn(() => ({
    runs: [] as ActiveRunResponse[],
    isLoading: false,
    error: null as string | null,
  })),
  mockApiFetch: vi.fn(),
  mockLoadDataSources: vi.fn(),
}));

vi.mock("../shell/use-active-runs", () => ({ useActiveRuns: mockUseActiveRuns }));
vi.mock("../api", () => ({
  apiFetch: mockApiFetch,
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

vi.mock("../shell/shell-store", () => ({
  useShellStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ currentProjectId: PROJECT_ID, accessMode: "local", sharedRole: "admin" }),
}));

vi.mock("../shell/notification-store", () => ({
  useNotificationStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ push: vi.fn() }),
}));

vi.mock("../shell/data-sources-store", () => ({
  useDataSourcesStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({
      dataSources: [
        {
          id: SOURCE_ID,
          name: "Test Source",
          protocol: "OPC UA",
          endpoint: "opc.tcp://localhost:4840",
          parameterCount: 5,
          status: "Active",
          health: "Healthy",
        },
      ],
      isLoading: false,
      loadDataSources: mockLoadDataSources,
    }),
}));

vi.mock("../shell/artifacts-store", () => ({
  useArtifactsStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({
      artifacts: [
        {
          id: ARTIFACT_ID,
          createdBy: "local",
          createdAt: "2026-01-01",
          valueCount: 3,
          protocol: "OPC UA",
        },
      ],
    }),
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  mockUseActiveRuns.mockReturnValue({ runs: [] as ActiveRunResponse[], isLoading: false, error: null });
});

function renderPage() {
  return render(
    <MemoryRouter initialEntries={[`/data-sources/${SOURCE_ID}/replay`]}>
      <Routes>
        <Route path="/data-sources/:sourceId/replay" element={<ReplayFlowPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

// Lazy import so mocks are registered first.
let ReplayFlowPage: typeof import("./replay-flow-page").ReplayFlowPage;
beforeAll(async () => {
  ({ ReplayFlowPage } = await import("./replay-flow-page"));
});

// ── source not yet in store (UI-472) ──────────────────────────────────────────

describe("ReplayFlowPage — source not yet in store (UI-472)", () => {
  it("reloads data sources once when the source isn't found locally", () => {
    render(
      <MemoryRouter initialEntries={["/data-sources/src-just-created/replay"]}>
        <Routes>
          <Route path="/data-sources/:sourceId/replay" element={<ReplayFlowPage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(mockLoadDataSources).toHaveBeenCalledWith(PROJECT_ID);
    expect(mockLoadDataSources).toHaveBeenCalledTimes(1);
  });
});

// ── completion detection (UI-126) ─────────────────────────────────────────────

describe("ReplayFlowPage — completion detection via active-runs poll", () => {
  it("transitions to completed when run disappears from active-runs after POST", async () => {
    // Start: active-runs returns empty (run not yet in list).
    mockUseActiveRuns.mockReturnValue({ runs: [] as ActiveRunResponse[], isLoading: false, error: null });

    // POST resolves immediately with a run that is RUNNING on the server.
    mockApiFetch.mockResolvedValue({
      recordingId: ARTIFACT_ID,
      dataSourceId: SOURCE_ID,
      valueCount: 3,
      runId: RUN_ID,
      evidenceId: "ev-1",
    });

    renderPage();

    // Click Start replay.
    await act(async () => {
      await userEvent.click(screen.getByRole("button", { name: /start replay/i }));
    });

    // After POST: runSeenRef.current = true and activeRuns = [] → effect drives to completed.
    await waitFor(() => {
      expect(screen.getByText("Done")).toBeTruthy();
    });
  });
});

// ── UI-464: idle badge hidden + "View evidence →" link on completion ─────────

describe("ReplayFlowPage — evidence badge and evidence link (UI-464)", () => {
  it("does not render the evidence badge in idle state", () => {
    renderPage();
    expect(screen.queryByText(/Evidence:/)).toBeNull();
  });

  it("shows View evidence link pointing to evidenceId after replay completes", async () => {
    mockApiFetch.mockResolvedValue({
      recordingId: ARTIFACT_ID,
      dataSourceId: SOURCE_ID,
      valueCount: 3,
      runId: RUN_ID,
      evidenceId: "ev-1",
    });

    renderPage();

    await act(async () => {
      await userEvent.click(screen.getByRole("button", { name: /start replay/i }));
    });

    await waitFor(() => {
      const link = screen.getByRole("link", { name: /view evidence/i });
      expect(link.getAttribute("href")).toBe("/evidence/ev-1");
    });
  });
});

// ── 409 schema-mismatch → "Run anyway" retry (UI-138) ────────────────────────

describe("ReplayFlowPage — 409 schema-mismatch → Run anyway (UI-138)", () => {
  it("shows schema-mismatch panel on 409 and retries with compatibilityAck=true on Run anyway", async () => {
    // First POST returns 409 (schema version mismatch).
    mockApiFetch.mockRejectedValueOnce(
      new ApiError(409, "Schema version mismatch", undefined, undefined),
    );
    // Second POST (Run anyway) succeeds.
    mockApiFetch.mockResolvedValueOnce({
      recordingId: ARTIFACT_ID,
      dataSourceId: SOURCE_ID,
      valueCount: 3,
      runId: RUN_ID,
      evidenceId: "ev-1",
    });

    renderPage();

    await act(async () => {
      await userEvent.click(screen.getByRole("button", { name: /start replay/i }));
    });

    // Schema-mismatch panel should appear after 409.
    await waitFor(() => {
      expect(screen.getByText("Schema version mismatch")).toBeTruthy();
    });

    // "Run anyway" button is visible and clicking it retries with compatibilityAck.
    await act(async () => {
      await userEvent.click(screen.getByRole("button", { name: /run anyway/i }));
    });

    await waitFor(() => {
      const calls = mockApiFetch.mock.calls;
      const retryCall = calls[calls.length - 1];
      const body = JSON.parse(retryCall[1].body as string) as Record<string, unknown>;
      expect(body.compatibilityAck).toBe(true);
    });
  });
});
