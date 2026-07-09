/**
 * Tests for DataSourceDetailPreviewPage
 *
 * Covers:
 * - Loading state shown when store is empty and fetch is in flight
 * - loadDataSources is called on mount when store is empty and not loading
 * - Error panel shown when source is not found after load completes
 * - loadDataSources is NOT called again after load completes (no infinite loop)
 * - Source details rendered when source is found in store
 * - IMPORT source: header shows recording link (UI-464)
 * - SYNTHETIC source: no Record or Replay recording buttons (UI-464)
 */

import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ActiveRunResponse } from "../shell/use-active-runs";

// ── hoisted mocks ─────────────────────────────────────────────────────────────

const {
  mockLoadDataSources,
  mockStopDataSource,
  mockDataSourcesStore,
  mockShellStore,
  mockUseActiveRuns,
} = vi.hoisted(() => {
  const loadDataSources = vi.fn();
  const stopDataSource = vi.fn();

  return {
    mockLoadDataSources: loadDataSources,
    mockStopDataSource: stopDataSource,
    mockDataSourcesStore: vi.fn(),
    mockShellStore: vi.fn(),
    mockUseActiveRuns: vi.fn(() => ({ runs: [] as ActiveRunResponse[], isLoading: false, error: null as string | null })),
  };
});

vi.mock("../shell/data-sources-store", () => ({
  useDataSourcesStore: mockDataSourcesStore,
}));

vi.mock("../shell/shell-store", () => ({
  useShellStore: mockShellStore,
}));

vi.mock("../shell/use-active-runs", () => ({
  useActiveRuns: mockUseActiveRuns,
}));

vi.mock("../shell/notification-store", () => ({
  useNotificationStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ push: vi.fn() }),
}));

vi.mock("../shell/artifacts-store", () => ({
  useArtifactsStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ artifacts: [], loadRecordings: vi.fn() }),
}));

// jsdom has no EventSource; the live hooks inside tab content need a stub
class StubEventSource {
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  addEventListener() {}
  close() {}
}
vi.stubGlobal("EventSource", StubEventSource as unknown as typeof EventSource);

import { DataSourceDetailPreviewPage } from "./data-source-detail-preview-page";

// ── helpers ───────────────────────────────────────────────────────────────────

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

const SOURCE_ID = "src-test";
const PROJECT_ID = "proj-1";

function setupShellStore() {
  mockShellStore.mockImplementation((sel: (s: Record<string, unknown>) => unknown) =>
    sel({ currentProjectId: PROJECT_ID, accessMode: "local", sharedRole: "admin" }),
  );
}

type StoreState = {
  dataSources: { id: string; name: string; protocol: string; endpoint: string; parameterCount: number; status: string; health: string }[];
  isLoading: boolean;
  loadDataSources: typeof mockLoadDataSources;
  stopDataSource: typeof mockStopDataSource;
};

function setupDataSourcesStore(partial: Partial<StoreState> = {}) {
  const state: StoreState = {
    dataSources: [],
    isLoading: false,
    loadDataSources: mockLoadDataSources,
    stopDataSource: mockStopDataSource,
    ...partial,
  };
  mockDataSourcesStore.mockImplementation((sel: (s: StoreState) => unknown) => sel(state));
}

const mockSource = {
  id: SOURCE_ID,
  name: "Test Source",
  protocol: "OPC UA",
  endpoint: "opc.tcp://localhost:4840",
  parameterCount: 10,
  status: "Stopped" as const,
  health: "Healthy" as const,
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={[`/data-sources/${SOURCE_ID}`]}>
      <Routes>
        <Route path="/data-sources/:sourceId" element={<DataSourceDetailPreviewPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

// ── loading state ─────────────────────────────────────────────────────────────

describe("DataSourceDetailPreviewPage — loading state", () => {
  it("shows loading panel while store is loading", () => {
    setupShellStore();
    setupDataSourcesStore({ dataSources: [], isLoading: true });

    renderPage();

    expect(screen.getByText(/Loading source/i)).toBeTruthy();
  });
});

// ── load-on-mount ─────────────────────────────────────────────────────────────

describe("DataSourceDetailPreviewPage — load-on-mount", () => {
  it("calls loadDataSources when store is empty and not loading", () => {
    setupShellStore();
    setupDataSourcesStore({ dataSources: [], isLoading: false });

    renderPage();

    expect(mockLoadDataSources).toHaveBeenCalledWith(PROJECT_ID);
    expect(mockLoadDataSources).toHaveBeenCalledTimes(1);
  });

  it("does not call loadDataSources when source is already in store", () => {
    setupShellStore();
    setupDataSourcesStore({ dataSources: [mockSource], isLoading: false });

    renderPage();

    expect(mockLoadDataSources).not.toHaveBeenCalled();
  });

  it("does not call loadDataSources while a fetch is already in flight", () => {
    setupShellStore();
    setupDataSourcesStore({ dataSources: [], isLoading: true });

    renderPage();

    expect(mockLoadDataSources).not.toHaveBeenCalled();
  });

  it("calls loadDataSources exactly once per mount even after multiple re-renders", () => {
    setupShellStore();
    setupDataSourcesStore({ dataSources: [], isLoading: false });

    const { rerender } = renderPage();

    // Force additional re-renders (simulates React state updates after load)
    rerender(
      <MemoryRouter initialEntries={[`/data-sources/${SOURCE_ID}`]}>
        <Routes>
          <Route path="/data-sources/:sourceId" element={<DataSourceDetailPreviewPage />} />
        </Routes>
      </MemoryRouter>,
    );
    rerender(
      <MemoryRouter initialEntries={[`/data-sources/${SOURCE_ID}`]}>
        <Routes>
          <Route path="/data-sources/:sourceId" element={<DataSourceDetailPreviewPage />} />
        </Routes>
      </MemoryRouter>,
    );

    // Despite multiple re-renders with source still absent, only called once
    expect(mockLoadDataSources).toHaveBeenCalledTimes(1);
  });
});

// ── error state ───────────────────────────────────────────────────────────────

describe("DataSourceDetailPreviewPage — error state", () => {
  it("shows error panel when source is not found after load", () => {
    setupShellStore();
    // loadDataSources is a no-op mock, so store stays empty after the effect runs;
    // the error panel renders because source is never populated
    setupDataSourcesStore({ dataSources: [], isLoading: false });
    renderPage();

    expect(screen.getByText(/This source could not be found/i)).toBeTruthy();
  });
});

// ── found state ───────────────────────────────────────────────────────────────

describe("DataSourceDetailPreviewPage — source found", () => {
  it("renders source name when source is in store", () => {
    setupShellStore();
    setupDataSourcesStore({ dataSources: [mockSource], isLoading: false });

    renderPage();

    expect(screen.getByText("Test Source")).toBeTruthy();
  });

  it("shows Record link for SCAN source (no Replay recording)", () => {
    setupShellStore();
    setupDataSourcesStore({
      dataSources: [{ ...mockSource, basis: "SCAN", realDeviceEndpoint: "opc.tcp://device:4840" }],
      isLoading: false,
    });

    renderPage();

    expect(screen.getAllByRole("link", { name: "Record" }).length).toBeGreaterThan(0);
    expect(screen.queryByRole("link", { name: "Replay recording" })).toBeNull();
    expect(screen.queryByRole("link", { name: "Simulate" })).toBeNull();
  });
});

// ── live simulation controls (UI-126) ─────────────────────────────────────────

describe("DataSourceDetailPreviewPage — live simulation badge", () => {
  const activeReplayRun = {
    id: "run-99",
    label: "Replay",
    processType: "Replay" as const,
    runState: "running" as const,
    startedAt: "2026-01-01T10:00:00Z",
    initiator: "local",
    relatedSourceId: SOURCE_ID,
    relatedLabel: null,
  };

  it("shows Simulating badge when a RUNNING Replay run is linked to this source", () => {
    setupShellStore();
    setupDataSourcesStore({ dataSources: [mockSource], isLoading: false });
    mockUseActiveRuns.mockReturnValue({ runs: [activeReplayRun], isLoading: false, error: null });

    renderPage();

    expect(screen.getByText("Simulating")).toBeTruthy();
  });

  it("shows Stop simulation button for admin when active replay run exists", () => {
    setupShellStore(); // admin role
    setupDataSourcesStore({ dataSources: [mockSource], isLoading: false });
    mockUseActiveRuns.mockReturnValue({ runs: [activeReplayRun], isLoading: false, error: null });

    renderPage();

    expect(screen.getByRole("button", { name: "Stop simulation" })).toBeTruthy();
  });

  it("hides Stop simulation button for shared user without canConfigureReplay", () => {
    mockShellStore.mockImplementation((sel: (s: Record<string, unknown>) => unknown) =>
      sel({ currentProjectId: PROJECT_ID, accessMode: "shared", sharedRole: "observer" }),
    );
    setupDataSourcesStore({ dataSources: [mockSource], isLoading: false });
    mockUseActiveRuns.mockReturnValue({ runs: [activeReplayRun], isLoading: false, error: null });

    renderPage();

    expect(screen.queryByRole("button", { name: "Stop simulation" })).toBeNull();
  });

  it("shows no Simulating badge when active run belongs to a different source", () => {
    setupShellStore();
    setupDataSourcesStore({ dataSources: [mockSource], isLoading: false });
    mockUseActiveRuns.mockReturnValue({
      runs: [{ ...activeReplayRun, relatedSourceId: "other-src" }],
      isLoading: false,
      error: null,
    });

    renderPage();

    expect(screen.queryByText("Simulating")).toBeNull();
  });
});

// ── IMPORT source header link (UI-464) ────────────────────────────────────────

describe("DataSourceDetailPreviewPage — IMPORT source recording link (UI-464)", () => {
  it("shows 'Replay recording' button for IMPORT source", () => {
    setupShellStore();
    setupDataSourcesStore({
      dataSources: [{ ...mockSource, basis: "IMPORT", runtimeConfig: null }],
      isLoading: false,
    });

    renderPage();

    expect(screen.getAllByRole("link", { name: "Replay recording" }).length).toBeGreaterThan(0);
    expect(screen.queryByRole("link", { name: "Record" })).toBeNull();
  });

  it("shows recording link in header when IMPORT source has importRecordingId in runtimeConfig", () => {
    setupShellStore();
    setupDataSourcesStore({
      dataSources: [
        {
          ...mockSource,
          basis: "IMPORT",
          runtimeConfig: JSON.stringify({ importRecordingId: "rec-abc" }),
        },
      ],
      isLoading: false,
    });

    renderPage();

    const recordingLink = screen.getByRole("link", { name: /Recording rec-abc/i });
    expect(recordingLink.getAttribute("href")).toContain("/recordings/rec-abc");
  });
});

// ── SYNTHETIC source suppresses Record / Replay recording (UI-464) ─────────────

describe("DataSourceDetailPreviewPage — SYNTHETIC source buttons (UI-464)", () => {
  it("shows Run button for SYNTHETIC source, no Record or Replay recording", () => {
    setupShellStore();
    setupDataSourcesStore({
      dataSources: [{ ...mockSource, basis: "SYNTHETIC" }],
      isLoading: false,
    });

    renderPage();

    expect(screen.getByRole("button", { name: "Run" })).toBeTruthy();
    expect(screen.queryByRole("link", { name: "Record" })).toBeNull();
    expect(screen.queryByRole("link", { name: "Replay recording" })).toBeNull();
    expect(screen.queryByRole("link", { name: "Simulate" })).toBeNull();
  });

  it("shows Stop button for active SYNTHETIC source", () => {
    setupShellStore();
    setupDataSourcesStore({
      dataSources: [{ ...mockSource, basis: "SYNTHETIC", status: "Active" as const }],
      isLoading: false,
    });

    renderPage();

    expect(screen.getByRole("button", { name: "Stop" })).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Run" })).toBeNull();
  });
});
