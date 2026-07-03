/**
 * Tests for DataSourceDetailPreviewPage load-on-mount fix
 *
 * Covers:
 * - Loading state shown when store is empty and fetch is in flight
 * - loadDataSources is called on mount when store is empty and not loading
 * - Error panel shown when source is not found after load completes
 * - loadDataSources is NOT called again after load completes (no infinite loop)
 * - Source details rendered when source is found in store
 */

import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";

// ── hoisted mocks ─────────────────────────────────────────────────────────────

const {
  mockLoadDataSources,
  mockStopDataSource,
  mockDataSourcesStore,
  mockShellStore,
} = vi.hoisted(() => {
  const loadDataSources = vi.fn();
  const stopDataSource = vi.fn();

  return {
    mockLoadDataSources: loadDataSources,
    mockStopDataSource: stopDataSource,
    mockDataSourcesStore: vi.fn(),
    mockShellStore: vi.fn(),
  };
});

vi.mock("../shell/data-sources-store", () => ({
  useDataSourcesStore: mockDataSourcesStore,
}));

vi.mock("../shell/shell-store", () => ({
  useShellStore: mockShellStore,
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

  it("shows Record and Simulate links when source is found", () => {
    setupShellStore();
    setupDataSourcesStore({ dataSources: [mockSource], isLoading: false });

    renderPage();

    expect(screen.getAllByRole("link", { name: "Record" }).length).toBeGreaterThan(0);
    expect(screen.getAllByRole("link", { name: "Simulate" }).length).toBeGreaterThan(0);
  });
});
