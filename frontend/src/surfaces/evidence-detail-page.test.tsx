/**
 * Tests for EvidenceDetailPage (UI-464)
 *
 * Covers:
 * - Navigation links: scenarioId, recordingId, and sourceId carry correct href values
 */

import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";

const EVIDENCE_ID = "ev-1";
const PROJECT_ID = "proj-1";
const SCENARIO_ID = "scen-abc";
const RECORDING_ID = "rec-xyz";
const SOURCE_ID = "src-001";

// ── hoisted mocks ──────────────────────────────────────────────────────────────

const { mockApiFetch } = vi.hoisted(() => ({
  mockApiFetch: vi.fn(),
}));

vi.mock("../api/client", () => ({
  apiFetch: mockApiFetch,
  authHeaders: () => ({}),
}));

vi.mock("../shell/shell-store", () => ({
  useShellStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ currentProjectId: PROJECT_ID, accessMode: "local", sharedRole: "admin" }),
}));

vi.mock("../shell/access-policy", () => ({
  resolveAccess: () => ({
    isAdmin: true,
    canConfigureReplay: true,
    canRecordSource: true,
    canStopSource: true,
    isSharedUser: false,
  }),
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

let EvidenceDetailPage: typeof import("./evidence-detail-page").EvidenceDetailPage;
beforeAll(async () => {
  ({ EvidenceDetailPage } = await import("./evidence-detail-page"));
});

function makeDto() {
  return {
    id: EVIDENCE_ID,
    runId: "run-99",
    status: "READY" as const,
    manifest: {
      formatVersion: "1.0",
      runId: "run-99",
      kind: "REPLAY" as const,
      trigger: "manual",
      initiator: "local",
      startedAt: "2026-01-01T00:00:00Z",
      endedAt: "2026-01-01T00:01:00Z",
      completeness: "COMPLETE" as const,
      sourceIds: [SOURCE_ID],
      scenarioId: SCENARIO_ID,
      recordingId: RECORDING_ID,
    },
    createdAt: "2026-01-01T00:00:00Z",
    createdBy: "local",
    exported: false,
  };
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={[`/evidence/${EVIDENCE_ID}`]}>
      <Routes>
        <Route path="/evidence/:evidenceId" element={<EvidenceDetailPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

// ── navigation links (UI-464) ─────────────────────────────────────────────────

describe("EvidenceDetailPage — navigation links (UI-464)", () => {
  it("renders a link to the scenario with correct href", async () => {
    mockApiFetch.mockResolvedValue(makeDto());
    renderPage();

    await waitFor(() => {
      const link = screen.getByRole("link", { name: SCENARIO_ID });
      expect(link.getAttribute("href")).toBe(`/scenarios/${SCENARIO_ID}`);
    });
  });

  it("renders a link to the recording with correct href", async () => {
    mockApiFetch.mockResolvedValue(makeDto());
    renderPage();

    await waitFor(() => {
      const link = screen.getByRole("link", { name: RECORDING_ID });
      expect(link.getAttribute("href")).toBe(`/recordings/${RECORDING_ID}`);
    });
  });

  it("renders a link to the source with correct href", async () => {
    mockApiFetch.mockResolvedValue(makeDto());
    renderPage();

    await waitFor(() => {
      const link = screen.getByRole("link", { name: SOURCE_ID });
      expect(link.getAttribute("href")).toBe(`/data-sources/${SOURCE_ID}`);
    });
  });
});
