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

const mockShellStore = vi.fn((selector: (s: { accessMode: string; sharedRole: string; currentProjectId: string }) => unknown) =>
  selector({ accessMode: "local", sharedRole: "observer", currentProjectId: "p1" }),
);
vi.mock("../shell/shell-store", () => ({ useShellStore: mockShellStore }));

const noop = () => {};
const mockDataSourcesStore = vi.fn((selector: (s: Record<string, unknown>) => unknown) =>
  selector({
    dataSources: [],
    startDataSource: noop,
    stopDataSource: noop,
    duplicateDataSource: noop,
    deleteDataSource: noop,
  }),
);
vi.mock("../shell/data-sources-store", () => ({ useDataSourcesStore: mockDataSourcesStore }));

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return { ...actual, useNavigate: () => vi.fn() };
});

afterEach(() => {
  cleanup();
  vi.resetModules();
});

// ── DataSourcesListPage stale/error branches ─────────────────────────────────

describe("DataSourcesListPage — sourceListError", () => {
  it("shows error panel when sourceListError is true", async () => {
    vi.doMock("../shell/mock-workspace", () => ({
      sourceListError: true,
      sourceListStale: false,
      mockExportShouldFail: false,
      stopActionCopy: { title: "", message: "", confirmLabel: "" },
    }));
    const { DataSourcesListPage } = await import("./data-sources-list-page");
    render(<MemoryRouter><DataSourcesListPage /></MemoryRouter>);
    expect(screen.getByText(/Data sources could not be loaded/)).toBeTruthy();
  });
});

describe("DataSourcesListPage — sourceListStale", () => {
  it("shows stale banner when sourceListStale is true", async () => {
    vi.doMock("../shell/mock-workspace", () => ({
      sourceListError: false,
      sourceListStale: true,
      mockExportShouldFail: false,
      stopActionCopy: { title: "", message: "", confirmLabel: "" },
    }));
    const { DataSourcesListPage } = await import("./data-sources-list-page");
    render(<MemoryRouter><DataSourcesListPage /></MemoryRouter>);
    expect(screen.getByText(/Source status may be outdated/)).toBeTruthy();
  });

  it("hides stale banner when sourceListStale is false", async () => {
    vi.doMock("../shell/mock-workspace", () => ({
      sourceListError: false,
      sourceListStale: false,
      mockExportShouldFail: false,
      stopActionCopy: { title: "", message: "", confirmLabel: "" },
    }));
    const { DataSourcesListPage } = await import("./data-sources-list-page");
    render(<MemoryRouter><DataSourcesListPage /></MemoryRouter>);
    expect(screen.queryByText(/Source status may be outdated/)).toBeNull();
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
