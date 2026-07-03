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

const SOURCE_ID = "src-1";
const PROJECT_ID = "proj-1";
const ARTIFACT_ID = "art-1";
const RUN_ID = "run-99";

// ── hoisted mocks ─────────────────────────────────────────────────────────────

const { mockUseActiveRuns, mockApiFetch } = vi.hoisted(() => ({
  mockUseActiveRuns: vi.fn(() => ({
    runs: [] as ActiveRunResponse[],
    isLoading: false,
    error: null as string | null,
  })),
  mockApiFetch: vi.fn(),
}));

vi.mock("../shell/use-active-runs", () => ({ useActiveRuns: mockUseActiveRuns }));
vi.mock("../api", () => ({ apiFetch: mockApiFetch }));

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
      expect(screen.getByText("completed")).toBeTruthy();
    });
  });
});
