/**
 * Tests for CreateRecordingWizardPage (UI-115, UI-118, UI-124)
 *
 * Covers:
 * - Step 1 (Data source): empty state, source list, Next enabled/disabled
 * - Step 2 (Scan type): shows options, selection persists to review
 * - Step 3 (Review): shows Create recording button, selected values
 */

import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CreateRecordingWizardPage } from "./create-recording-wizard-page";

const { mockNavigate, mockApiFetch } = vi.hoisted(() => ({
  mockNavigate: vi.fn(),
  mockApiFetch: vi.fn(),
}));

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock("../api", () => ({
  apiFetch: mockApiFetch,
  ApiError: class ApiError extends Error {
    constructor(
      public readonly status: number,
      public readonly title: string,
      public readonly detail: string | undefined,
      public readonly type: string | undefined,
    ) { super(title); this.name = "ApiError"; }
  },
}));

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

async function navigateToScanTypeStep() {
  renderPage();
  await userEvent.click(screen.getByText("Plant OPC UA"));
  await userEvent.click(screen.getByRole("button", { name: "Next" }));
}

async function navigateToReviewStep() {
  await navigateToScanTypeStep();
  await userEvent.click(screen.getByRole("button", { name: "Next" }));
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

describe("CreateRecordingWizardPage — step 2 (Scan type)", () => {
  it("shows both scan type options", async () => {
    await navigateToScanTypeStep();
    expect(screen.getByText("Schema + data")).toBeTruthy();
    expect(screen.getByText("Schema only")).toBeTruthy();
  });

  it("Schema + data is selected by default", async () => {
    await navigateToScanTypeStep();
    // The default button should have the selected style (border-shell-accent)
    const schemaAndDataBtn = screen.getByText("Schema + data").closest("button")!;
    expect(schemaAndDataBtn.className).toContain("border-shell-accent");
  });

  it("Schema only can be selected", async () => {
    await navigateToScanTypeStep();
    await userEvent.click(screen.getByText("Schema only").closest("button")!);
    const schemaOnlyBtn = screen.getByText("Schema only").closest("button")!;
    expect(schemaOnlyBtn.className).toContain("border-shell-accent");
  });

  it("Next is always enabled on scan type step", async () => {
    await navigateToScanTypeStep();
    const btn = screen.getByRole("button", { name: "Next" }) as HTMLButtonElement;
    expect(btn.disabled).toBe(false);
  });
});

describe("CreateRecordingWizardPage — step 3 (Review)", () => {
  it("renders Start capture button on review step (default scan type is SCHEMA_AND_DATA)", async () => {
    await navigateToReviewStep();
    expect(screen.getByRole("button", { name: "Start capture" })).toBeTruthy();
  });

  it("renders Create recording button on review step when SCHEMA_ONLY selected", async () => {
    await navigateToScanTypeStep();
    await userEvent.click(screen.getByText("Schema only").closest("button")!);
    await userEvent.click(screen.getByRole("button", { name: "Next" }));
    expect(screen.getByRole("button", { name: "Create recording" })).toBeTruthy();
  });

  it("shows selected source name in review", async () => {
    await navigateToReviewStep();
    expect(screen.getAllByText("Plant OPC UA").length).toBeGreaterThanOrEqual(1);
  });

  it("shows scan type in review (default: Schema + data)", async () => {
    await navigateToReviewStep();
    expect(screen.getByText("Schema + data")).toBeTruthy();
  });

  it("shows Schema only in review when selected", async () => {
    await navigateToScanTypeStep();
    await userEvent.click(screen.getByText("Schema only").closest("button")!);
    await userEvent.click(screen.getByRole("button", { name: "Next" }));
    expect(screen.getByText("Schema only")).toBeTruthy();
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

// ── POST body: name field (UI-131) ────────────────────────────────────────────

async function navigateToReviewSchemaOnly() {
  await navigateToScanTypeStep();
  await userEvent.click(screen.getByText("Schema only").closest("button")!);
  await userEvent.click(screen.getByRole("button", { name: "Next" }));
}

describe("CreateRecordingWizardPage — POST body name (UI-131)", () => {
  it("includes trimmed name in POST body when non-empty", async () => {
    mockApiFetch.mockResolvedValue({ id: "rec-42" });
    await navigateToReviewSchemaOnly();

    const nameInput = screen.getByLabelText(/recording name/i);
    await userEvent.type(nameInput, "  My Pump Scan  ");

    await userEvent.click(screen.getByRole("button", { name: "Create recording" }));

    const body = JSON.parse(mockApiFetch.mock.calls[0][1].body as string) as Record<string, unknown>;
    expect(body.name).toBe("My Pump Scan");
  });

  it("omits name from POST body when left blank", async () => {
    mockApiFetch.mockResolvedValue({ id: "rec-43" });
    await navigateToReviewSchemaOnly();

    await userEvent.click(screen.getByRole("button", { name: "Create recording" }));

    const body = JSON.parse(mockApiFetch.mock.calls[0][1].body as string) as Record<string, unknown>;
    expect(Object.prototype.hasOwnProperty.call(body, "name")).toBe(false);
  });
});

// ── UI-132 routing ────────────────────────────────────────────────────────────

describe("CreateRecordingWizardPage — UI-132 routing", () => {
  it("Start capture navigates to /data-sources/:id/recording without API call (SCHEMA_AND_DATA)", async () => {
    await navigateToReviewStep();
    await userEvent.click(screen.getByRole("button", { name: "Start capture" }));
    expect(mockNavigate).toHaveBeenCalledWith("/data-sources/src-1/recording");
    expect(mockApiFetch).not.toHaveBeenCalled();
  });

  it("Create recording calls POST /recordings and navigates to detail page (SCHEMA_ONLY)", async () => {
    mockApiFetch.mockResolvedValueOnce({ id: "rec-42" });
    await navigateToScanTypeStep();
    await userEvent.click(screen.getByText("Schema only").closest("button")!);
    await userEvent.click(screen.getByRole("button", { name: "Next" }));
    await userEvent.click(screen.getByRole("button", { name: "Create recording" }));
    expect(mockApiFetch).toHaveBeenCalledWith(
      "/api/v1/projects/proj-1/recordings",
      expect.objectContaining({ method: "POST" }),
    );
    expect(mockNavigate).toHaveBeenCalledWith("/recordings/rec-42");
  });

  it("Start capture is disabled when source has no real device endpoint (SCHEMA_AND_DATA blocked)", async () => {
    setupSources([
      { id: "src-empty", name: "No Endpoint Source", protocol: "OPC UA", status: "Stopped", endpoint: "" },
    ]);
    render(
      <MemoryRouter>
        <CreateRecordingWizardPage />
      </MemoryRouter>,
    );
    await userEvent.click(screen.getByText("No Endpoint Source"));
    await userEvent.click(screen.getByRole("button", { name: "Next" }));
    expect(screen.getByText("Live capture not available for this source.")).toBeTruthy();
    await userEvent.click(screen.getByRole("button", { name: "Next" }));
    const startBtn = screen.getByRole("button", { name: "Start capture" }) as HTMLButtonElement;
    expect(startBtn.disabled).toBe(true);
  });
});
