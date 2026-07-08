/**
 * Tests for CreateRecordingWizardPage (UI-462)
 *
 * Covers:
 * - Step 1 (Data source): list, Next enabled/disabled, sources without realDeviceEndpoint disabled
 * - Step 2 (Review): shows source summary and Start capture button
 * - Start capture navigates to /data-sources/:id/recording
 * - Empty sources state
 * - loadDataSources called on mount
 */

import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CreateRecordingWizardPage } from "./create-recording-wizard-page";

const { mockNavigate, mockLoadDataSources, mockDataSourcesStore } = vi.hoisted(() => ({
  mockNavigate: vi.fn(),
  mockLoadDataSources: vi.fn().mockResolvedValue(undefined),
  mockDataSourcesStore: vi.fn(),
}));

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock("../shell/shell-store", () => ({
  useShellStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ accessMode: "local", sharedRole: "admin", currentProjectId: "proj-1" }),
}));

vi.mock("../shell/notification-store", () => ({
  useNotificationStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ push: vi.fn() }),
}));

vi.mock("../shell/data-sources-store", () => ({
  useDataSourcesStore: mockDataSourcesStore,
}));

const defaultSources = [
  {
    id: "src-1",
    name: "Plant OPC UA",
    protocol: "OPC UA",
    status: "Active",
    endpoint: "opc.tcp://plant:4840",
    realDeviceEndpoint: "opc.tcp://plant:4840",
  },
  {
    id: "src-2",
    name: "Modbus Line B",
    protocol: "Modbus TCP",
    status: "Stopped",
    endpoint: "",
    realDeviceEndpoint: null,
  },
];

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function setupSources(sources: typeof defaultSources) {
  mockDataSourcesStore.mockImplementation((selector: (s: Record<string, unknown>) => unknown) =>
    selector({ dataSources: sources, loadDataSources: mockLoadDataSources }),
  );
}

function renderPage() {
  setupSources(defaultSources);
  render(
    <MemoryRouter>
      <CreateRecordingWizardPage />
    </MemoryRouter>,
  );
}

async function navigateToReviewStep() {
  renderPage();
  await userEvent.click(screen.getByText("Plant OPC UA"));
  await userEvent.click(screen.getByRole("button", { name: "Next" }));
}

// ---------------------------------------------------------------------------
// Step 1 — Data source
// ---------------------------------------------------------------------------
describe("CreateRecordingWizardPage — step 1 (Data source)", () => {
  it("renders data source list", () => {
    renderPage();
    expect(screen.getByText("Plant OPC UA")).toBeTruthy();
    expect(screen.getByText("Modbus Line B")).toBeTruthy();
  });

  it("Next is disabled when no source selected", () => {
    renderPage();
    const btn = screen.getByRole("button", { name: "Next" }) as HTMLButtonElement;
    expect(btn.disabled).toBe(true);
  });

  it("Next enabled after selecting a source with realDeviceEndpoint", async () => {
    renderPage();
    await userEvent.click(screen.getByText("Plant OPC UA"));
    const btn = screen.getByRole("button", { name: "Next" }) as HTMLButtonElement;
    expect(btn.disabled).toBe(false);
  });

  it("source without realDeviceEndpoint is shown as disabled", () => {
    renderPage();
    const modbusBtn = screen.getByText("Modbus Line B").closest("button") as HTMLButtonElement;
    expect(modbusBtn.disabled).toBe(true);
  });

  it("source without realDeviceEndpoint shows unavailable note", () => {
    renderPage();
    expect(screen.getByText(/No real device endpoint — live capture unavailable/i)).toBeTruthy();
  });
});

// ---------------------------------------------------------------------------
// Step 2 — Review
// ---------------------------------------------------------------------------
describe("CreateRecordingWizardPage — step 2 (Review)", () => {
  it("shows Start capture button on review step", async () => {
    await navigateToReviewStep();
    expect(screen.getByRole("button", { name: "Start capture" })).toBeTruthy();
  });

  it("shows selected source name in review", async () => {
    await navigateToReviewStep();
    expect(screen.getAllByText("Plant OPC UA").length).toBeGreaterThanOrEqual(1);
  });

  it("shows 'This will open the live capture flow.' info panel", async () => {
    await navigateToReviewStep();
    expect(screen.getByText("This will open the live capture flow.")).toBeTruthy();
  });
});

// ---------------------------------------------------------------------------
// Routing
// ---------------------------------------------------------------------------
describe("CreateRecordingWizardPage — routing", () => {
  it("Start capture navigates to /data-sources/:id/recording without API call", async () => {
    await navigateToReviewStep();
    await userEvent.click(screen.getByRole("button", { name: "Start capture" }));
    expect(mockNavigate).toHaveBeenCalledWith("/data-sources/src-1/recording");
  });
});

// ---------------------------------------------------------------------------
// Empty sources
// ---------------------------------------------------------------------------
describe("CreateRecordingWizardPage — empty sources", () => {
  it("shows empty state when no data sources exist", () => {
    setupSources([]);
    render(
      <MemoryRouter>
        <CreateRecordingWizardPage />
      </MemoryRouter>,
    );
    expect(screen.getByText("No data sources available.")).toBeTruthy();
  });
});

// ---------------------------------------------------------------------------
// loadDataSources on mount
// ---------------------------------------------------------------------------
describe("CreateRecordingWizardPage — loadDataSources on mount", () => {
  it("calls loadDataSources with currentProjectId on mount", () => {
    renderPage();
    expect(mockLoadDataSources).toHaveBeenCalledWith("proj-1");
  });

  it("calls loadDataSources exactly once on mount", () => {
    renderPage();
    expect(mockLoadDataSources).toHaveBeenCalledTimes(1);
  });
});
