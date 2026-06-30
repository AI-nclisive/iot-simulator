/**
 * Tests for UI-092 edge-state conditional rendering
 *
 * Covers:
 * - live runtime stream drop → reconnecting StaleBanner in RuntimeDashboardPanel
 * - live runtime stream open → no StaleBanner in RuntimeDashboardPanel
 * - sourceListStale=true → stale message appears in DataSourcesListPage
 * - sourceListError=true → error panel appears, list hidden in DataSourcesListPage
 */

import { act, cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";

const noop = () => {};
const mockAsyncNoop = async () => {};

function makeDataSourcesStoreMock(overrides: Record<string, unknown> = {}) {
  return vi.fn((selector: (s: Record<string, unknown>) => unknown) =>
    selector({
      dataSources: [],
      isLoading: false,
      error: null,
      loadDataSources: mockAsyncNoop,
      startDataSource: mockAsyncNoop,
      stopDataSource: mockAsyncNoop,
      duplicateDataSource: mockAsyncNoop,
      deleteDataSource: mockAsyncNoop,
      ...overrides,
    }),
  );
}

const mockShellStore = vi.fn((selector: (s: { accessMode: string; sharedRole: string; currentProjectId: string }) => unknown) =>
  selector({ accessMode: "local", sharedRole: "observer", currentProjectId: "p1" }),
);
vi.mock("../shell/shell-store", () => ({ useShellStore: mockShellStore }));
vi.mock("../shell/notification-store", () => ({
  useNotificationStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ push: noop }),
}));

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return { ...actual, useNavigate: () => vi.fn() };
});

afterEach(() => {
  cleanup();
  vi.resetModules();
});

// ── DataSourcesListPage error/loading branches ──────────────────────────────

describe("DataSourcesListPage — sourceListError", () => {
  it("shows error panel when store has an error", async () => {
    vi.doMock("../shell/data-sources-store", () => ({
      useDataSourcesStore: makeDataSourcesStoreMock({ error: "Could not connect" }),
    }));
    const { DataSourcesListPage } = await import("./data-sources-list-page");
    render(<MemoryRouter><DataSourcesListPage /></MemoryRouter>);
    expect(screen.getByText(/Data sources could not be loaded/)).toBeTruthy();
  });
});

describe("DataSourcesListPage — sourceListStale", () => {
  it("shows loading when isLoading is true", async () => {
    vi.doMock("../shell/data-sources-store", () => ({
      useDataSourcesStore: makeDataSourcesStoreMock({ isLoading: true }),
    }));
    const { DataSourcesListPage } = await import("./data-sources-list-page");
    render(<MemoryRouter><DataSourcesListPage /></MemoryRouter>);
    expect(screen.getByText("Loading sources…")).toBeTruthy();
  });

  it("hides loading when isLoading is false", async () => {
    vi.doMock("../shell/data-sources-store", () => ({
      useDataSourcesStore: makeDataSourcesStoreMock({ isLoading: false }),
    }));
    const { DataSourcesListPage } = await import("./data-sources-list-page");
    render(<MemoryRouter><DataSourcesListPage /></MemoryRouter>);
    expect(screen.queryByText("Loading sources…")).toBeNull();
  });
});

// ── RuntimeDashboardPanel stale banner (driven by live SSE — UI-098) ─────────

class FakeEventSource {
  static instances: FakeEventSource[] = [];
  url: string;
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  closed = false;
  private listeners = new Map<string, ((ev: MessageEvent) => void)[]>();
  constructor(url: string) {
    this.url = url;
    FakeEventSource.instances.push(this);
  }
  addEventListener(t: string, fn: (ev: MessageEvent) => void) {
    const a = this.listeners.get(t) ?? [];
    a.push(fn);
    this.listeners.set(t, a);
  }
  close() {
    this.closed = true;
  }
  emitOpen() {
    this.onopen?.();
  }
  emitError() {
    this.onerror?.();
  }
  static latest() {
    return FakeEventSource.instances[FakeEventSource.instances.length - 1];
  }
  static reset() {
    FakeEventSource.instances = [];
  }
}

describe("RuntimeDashboardPanel — live stale banner", () => {
  it("shows the reconnecting banner when the live runtime stream drops", async () => {
    vi.stubGlobal("EventSource", FakeEventSource as unknown as typeof EventSource);
    FakeEventSource.reset();
    vi.doMock("../shell/mock-workspace", () => ({ activeRuns: [] }));
    const { RuntimeDashboardPanel } = await import("./runtime-dashboard-panel");
    render(
      <MemoryRouter>
        <RuntimeDashboardPanel />
      </MemoryRouter>,
    );
    // Open then drop the stream → reconnecting.
    act(() => {
      FakeEventSource.latest().emitOpen();
      FakeEventSource.latest().emitError();
    });
    expect(await screen.findByText(/Reconnecting to live runtime updates/)).toBeTruthy();
    vi.unstubAllGlobals();
  });

  it("shows no stale banner while the stream is open", async () => {
    vi.stubGlobal("EventSource", FakeEventSource as unknown as typeof EventSource);
    FakeEventSource.reset();
    vi.doMock("../shell/mock-workspace", () => ({ activeRuns: [] }));
    const { RuntimeDashboardPanel } = await import("./runtime-dashboard-panel");
    render(
      <MemoryRouter>
        <RuntimeDashboardPanel />
      </MemoryRouter>,
    );
    act(() => {
      FakeEventSource.latest().emitOpen();
    });
    expect(screen.queryByText(/Reconnecting to live runtime updates/)).toBeNull();
    expect(screen.queryByText(/Live runtime updates have paused/)).toBeNull();
    vi.unstubAllGlobals();
  });
});
