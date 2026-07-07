/**
 * Tests for DataSourcesListPage row actions (UI-455) and parameter count display (UI-456)
 *
 * Covers:
 * - IMPORT-basis source must not show the "Record" row action
 * - IMPORT-basis source must label the replay action "Replay recording" (not "Simulate")
 * - SCAN-basis source shows both "Record" and "Simulate"
 * - parameterCount from store is rendered in the list row
 */

import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";

const { mockNavigate } = vi.hoisted(() => ({ mockNavigate: vi.fn() }));
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock("../shell/shell-store", () => ({
  useShellStore: (sel: (s: object) => unknown) =>
    sel({ accessMode: "local", sharedRole: "admin", currentProjectId: "proj-1" }),
}));

vi.mock("../shell/notification-store", () => ({
  useNotificationStore: (sel: (s: object) => unknown) => sel({ push: vi.fn() }),
}));

vi.mock("../shell/use-active-runs", () => ({
  useActiveRuns: () => ({ runs: [] }),
}));

const { mockDataSourcesStore } = vi.hoisted(() => ({ mockDataSourcesStore: vi.fn() }));
vi.mock("../shell/data-sources-store", () => ({
  useDataSourcesStore: mockDataSourcesStore,
}));

import { DataSourcesListPage } from "./data-sources-list-page";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

const baseSource = {
  id: "src-1",
  name: "Test Source",
  protocol: "OPC UA" as const,
  endpoint: "opc.tcp://localhost:4840",
  parameterCount: 0,
  status: "Stopped" as const,
  health: "Healthy" as const,
};

function setupStore(sources: object[]) {
  mockDataSourcesStore.mockImplementation((sel: (s: object) => unknown) =>
    sel({
      dataSources: sources,
      isLoading: false,
      error: null,
      loadDataSources: vi.fn().mockResolvedValue(undefined),
      stopDataSource: vi.fn(),
      duplicateDataSource: vi.fn(),
      deleteDataSource: vi.fn(),
    }),
  );
}

function renderPage() {
  return render(
    <MemoryRouter>
      <DataSourcesListPage />
    </MemoryRouter>,
  );
}

describe("DataSourcesListPage — row actions for IMPORT basis (UI-455)", () => {
  it("IMPORT source does not show 'Record' action", async () => {
    setupStore([{ ...baseSource, basis: "IMPORT", realDeviceEndpoint: null }]);
    renderPage();
    await waitFor(() => expect(screen.getByText("Test Source")).toBeTruthy());
    expect(screen.queryByRole("button", { name: "Record" })).toBeNull();
    expect(screen.queryByText("Record")).toBeNull();
  });

  it("IMPORT source shows 'Replay recording' action (not 'Simulate')", async () => {
    setupStore([{ ...baseSource, basis: "IMPORT", realDeviceEndpoint: null }]);
    renderPage();
    await waitFor(() => expect(screen.getByRole("button", { name: "Replay recording" })).toBeTruthy());
    expect(screen.queryByRole("button", { name: "Simulate" })).toBeNull();
  });

  it("SCAN source shows 'Record' action", async () => {
    setupStore([{ ...baseSource, basis: "SCAN", realDeviceEndpoint: "opc.tcp://device:4840" }]);
    renderPage();
    await waitFor(() => expect(screen.getByRole("button", { name: "Record" })).toBeTruthy());
  });

  it("SCAN source shows 'Simulate' action (not 'Replay recording')", async () => {
    setupStore([{ ...baseSource, basis: "SCAN", realDeviceEndpoint: "opc.tcp://device:4840" }]);
    renderPage();
    await waitFor(() => expect(screen.getByRole("button", { name: "Simulate" })).toBeTruthy());
    expect(screen.queryByRole("button", { name: "Replay recording" })).toBeNull();
  });
});

describe("DataSourcesListPage — parameter count display (UI-456)", () => {
  it("renders parameterCount from store in the list row", async () => {
    setupStore([{ ...baseSource, parameterCount: 2480 }]);
    renderPage();
    await waitFor(() => expect(screen.getByText("Test Source")).toBeTruthy());
    expect(screen.getByText(/2,480 parameters/)).toBeTruthy();
  });

  it("renders parameterCount of 0 when store value is 0", async () => {
    setupStore([{ ...baseSource, parameterCount: 0 }]);
    renderPage();
    await waitFor(() => expect(screen.getByText("Test Source")).toBeTruthy());
    expect(screen.getByText(/0 parameters/)).toBeTruthy();
  });
});
