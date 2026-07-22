/**
 * Tests for EvidenceListPage — Sources column name resolution (UI-500)
 *
 * Covers:
 * - a single source resolves to its name instead of showing the raw UUID
 * - multiple sources resolve to a comma-joined list of names instead of just a count
 * - an unresolvable source id falls back to the raw id
 */

import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";

const { mockApiFetch } = vi.hoisted(() => ({ mockApiFetch: vi.fn() }));
vi.mock("../api/client", () => ({
  apiFetch: mockApiFetch,
  authHeaders: () => ({}),
}));

vi.mock("../shell/shell-store", () => ({
  useShellStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ currentProjectId: "proj-1", accessMode: "local", sharedRole: "admin" }),
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

const { mockDataSources } = vi.hoisted(() => ({
  mockDataSources: { current: [] as { id: string; name: string }[] },
}));
vi.mock("../shell/data-sources-store", () => ({
  useDataSourcesStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ dataSources: mockDataSources.current, loadDataSources: () => Promise.resolve() }),
}));

import { EvidenceListPage } from "./evidence-list-page";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  mockDataSources.current = [];
});

function makeItem(overrides: Record<string, unknown> = {}) {
  return {
    id: "ev-1",
    runId: "run-1",
    status: "READY",
    manifest: {
      formatVersion: "1.0",
      runId: "run-1",
      kind: "REPLAY",
      trigger: "manual",
      initiator: "local",
      startedAt: "2026-01-01T00:00:00Z",
      endedAt: "2026-01-01T00:01:00Z",
      completeness: "COMPLETE",
      sourceIds: ["src-1"],
      scenarioId: null,
      recordingId: null,
      ...overrides,
    },
    createdAt: "2026-01-01T00:00:00Z",
    createdBy: "local",
    exported: false,
  };
}

function renderPage() {
  return render(
    <MemoryRouter>
      <EvidenceListPage />
    </MemoryRouter>,
  );
}

describe("EvidenceListPage — Sources column name resolution (UI-500)", () => {
  it("resolves a single source id to its name", async () => {
    mockDataSources.current = [{ id: "src-1", name: "Boiler Feed Pump" }];
    mockApiFetch.mockResolvedValue({ items: [makeItem({ sourceIds: ["src-1"] })], nextCursor: null });
    renderPage();

    await waitFor(() => expect(screen.getByText("Boiler Feed Pump")).not.toBeNull());
    expect(screen.queryByText("src-1")).toBeNull();
  });

  it("resolves multiple source ids to a comma-joined list of names, not just a count", async () => {
    mockDataSources.current = [
      { id: "src-1", name: "Boiler Feed Pump" },
      { id: "src-2", name: "Cooling Fan" },
    ];
    mockApiFetch.mockResolvedValue({
      items: [makeItem({ sourceIds: ["src-1", "src-2"] })],
      nextCursor: null,
    });
    renderPage();

    await waitFor(() =>
      expect(screen.getByText("Boiler Feed Pump, Cooling Fan")).not.toBeNull(),
    );
    expect(screen.queryByText("2 sources")).toBeNull();
  });

  it("falls back to the raw id when the source can't be resolved", async () => {
    mockDataSources.current = [];
    mockApiFetch.mockResolvedValue({ items: [makeItem({ sourceIds: ["src-missing"] })], nextCursor: null });
    renderPage();

    await waitFor(() => expect(screen.getByText("src-missing")).not.toBeNull());
  });
});
