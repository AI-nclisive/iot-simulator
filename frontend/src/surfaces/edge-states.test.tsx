/**
 * Tests for UI-092 edge-state conditional rendering
 *
 * Covers:
 * - dashboardStale=true → StaleBanner appears in RuntimeDashboardPanel
 * - dashboardStale=false → StaleBanner absent in RuntimeDashboardPanel
 * - sourceListStale=true → stale message appears in DataSourcesListPage
 * - sourceListError=true → error panel appears, list hidden in DataSourcesListPage
 */

import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";

const mockShellStore = vi.fn((selector: (s: { accessMode: string; sharedRole: string }) => unknown) =>
  selector({ accessMode: "local", sharedRole: "observer" }),
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

// ── RuntimeDashboardPanel stale banner ───────────────────────────────────────

describe("RuntimeDashboardPanel — dashboardStale", () => {
  it("shows stale banner when dashboardStale is true", async () => {
    vi.doMock("../shell/mock-workspace", () => ({
      activeRuns: [],
      dashboardStale: true,
    }));
    const { RuntimeDashboardPanel } = await import("./runtime-dashboard-panel");
    render(
      <MemoryRouter>
        <RuntimeDashboardPanel />
      </MemoryRouter>,
    );
    expect(
      screen.getByText(/Dashboard data may be outdated/),
    ).toBeTruthy();
  });

  it("hides stale banner when dashboardStale is false", async () => {
    vi.doMock("../shell/mock-workspace", () => ({
      activeRuns: [],
      dashboardStale: false,
    }));
    const { RuntimeDashboardPanel } = await import("./runtime-dashboard-panel");
    render(
      <MemoryRouter>
        <RuntimeDashboardPanel />
      </MemoryRouter>,
    );
    expect(screen.queryByText(/Dashboard data may be outdated/)).toBeNull();
  });
});
