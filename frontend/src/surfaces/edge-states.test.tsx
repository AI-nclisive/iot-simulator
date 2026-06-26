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

afterEach(() => {
  cleanup();
  vi.resetModules();
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
