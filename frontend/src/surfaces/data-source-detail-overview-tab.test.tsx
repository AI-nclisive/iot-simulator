/**
 * Tests for DataSourceDetailOverviewTab (UI-474)
 *
 * Covers:
 * - Long endpoint URLs wrap (break-all) instead of overflowing their card
 */

import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it } from "vitest";
import { DataSourceDetailOverviewTab } from "./data-source-detail-overview-tab";
import type { DataSourceRow } from "./mock-data-sources";

afterEach(() => {
  cleanup();
});

const longEndpoint = "opc.tcp://uademo.prosysopc.com:53530/OPCUA/SimulationServer";

function makeSource(overrides: Partial<DataSourceRow> = {}): DataSourceRow {
  return {
    id: "src-1",
    name: "Test source",
    protocol: "OPC UA",
    endpoint: "opc.tcp://localhost:4840/iotsim",
    basis: "SCAN",
    realDeviceEndpoint: longEndpoint,
    parameterCount: 250,
    status: "Stopped",
    health: "Healthy",
    ...overrides,
  };
}

function renderTab(source: DataSourceRow) {
  return render(
    <MemoryRouter>
      <DataSourceDetailOverviewTab source={source} />
    </MemoryRouter>,
  );
}

describe("DataSourceDetailOverviewTab — long endpoint wrapping (UI-474)", () => {
  it("applies break-all to the real device endpoint value", () => {
    renderTab(makeSource());

    const dd = screen.getByText(longEndpoint);
    expect(dd.className).toContain("break-all");
  });

  it("applies break-all to the simulator serve URL value", () => {
    const serveUrl = "opc.tcp://a-very-long-simulator-hostname-for-testing.local:4840/iotsim";
    renderTab(makeSource({ endpoint: serveUrl, basis: "MANUAL", realDeviceEndpoint: null }));

    const dd = screen.getByText(serveUrl);
    expect(dd.className).toContain("break-all");
  });
});
