/**
 * Tests for CreateRecordingWizardPage (UI-115, UI-118)
 *
 * Covers:
 * - Step 1 (Data source): shows empty state when no sources available
 * - Step 1: shows data sources; Next disabled until one selected
 * - Step 1: Next enabled after selecting a source
 * - Step 2 (Review): shows Create recording button
 */

import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CreateRecordingWizardPage } from "./create-recording-wizard-page";

const { mockNavigate } = vi.hoisted(() => ({ mockNavigate: vi.fn() }));

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

const { mockDataSourcesStore } = vi.hoisted(() => ({ mockDataSourcesStore: vi.fn() }));

vi.mock("../shell/data-sources-store", () => ({
  useDataSourcesStore: mockDataSourcesStore,
}));

const defaultSources = [
  { id: "src-1", name: "Plant OPC UA", protocol: "OPC UA", status: "Active", endpoint: "opc.tcp://plant:4840" },
  { id: "src-2", name: "Modbus Line B", protocol: "Modbus TCP", status: "Stopped", endpoint: "" },
];

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function setupSources(sources: typeof defaultSources) {
  mockDataSourcesStore.mockImplementation((selector: (s: Record<string, unknown>) => unknown) =>
    selector({ dataSources: sources }),
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

  it("Next enabled after selecting a source", async () => {
    renderPage();
    await userEvent.click(screen.getByText("Plant OPC UA"));
    const btn = screen.getByRole("button", { name: "Next" }) as HTMLButtonElement;
    expect(btn.disabled).toBe(false);
  });
});

describe("CreateRecordingWizardPage — step 2 (Review)", () => {
  it("renders Create recording button on review step", async () => {
    renderPage();
    await userEvent.click(screen.getByText("Plant OPC UA"));
    await userEvent.click(screen.getByRole("button", { name: "Next" }));
    expect(screen.getByRole("button", { name: "Create recording" })).toBeTruthy();
  });

  it("shows selected source name in review", async () => {
    renderPage();
    await userEvent.click(screen.getByText("Plant OPC UA"));
    await userEvent.click(screen.getByRole("button", { name: "Next" }));
    expect(screen.getAllByText("Plant OPC UA").length).toBeGreaterThanOrEqual(1);
  });
});

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
