/**
 * Tests for CreateDataSourceWizardPage (UI-037, UI-116, UI-121, UI-458)
 *
 * Covers:
 * - Source name field is shown on the scan setup step
 * - Next is disabled when source name is empty (validation system active)
 * - Next is enabled when source name is filled on scan path
 * - validationMessage skips credential validation in local mode (UI-116)
 * - validationMessage enforces credentials in shared mode
 * - UI-458: SCAN scan step — auto-start, polling, complete state, error+retry,
 *   unknown type resolution, Next button validation, create-from-scan endpoint
 */

import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  CreateDataSourceWizardPage,
  scanStepValidationMessage,
  validationMessage,
  type DiscoveredNodeResponse,
  type TypeResolutionEntry,
  type WizardFormState,
} from "./create-data-source-wizard-page";

const { mockNavigate, shellStoreState } = vi.hoisted(() => ({
  mockNavigate: vi.fn(),
  shellStoreState: {
    accessMode: "local" as "local" | "shared",
    sharedRole: "admin",
    currentProjectId: "proj-test",
  },
}));

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock("../shell/shell-store", () => ({
  useShellStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector(shellStoreState as unknown as Record<string, unknown>),
}));

vi.mock("../shell/data-sources-store", () => ({
  useDataSourcesStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({
      createDataSource: vi.fn(() => "src-new"),
      createSyntheticSource: vi.fn(() => "src-syn"),
      loadDataSources: vi.fn(),
      dataSources: [],
    }),
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

async function navigateToScanSetup() {
  render(
    <MemoryRouter>
      <CreateDataSourceWizardPage />
    </MemoryRouter>,
  );

  // Step 0: choose protocol
  await userEvent.click(screen.getByText("OPC UA"));
  await userEvent.click(screen.getByRole("button", { name: "Next" }));

  // Step 1: choose Real source basis
  await userEvent.click(screen.getByText("Real source"));
  await userEvent.click(screen.getByRole("button", { name: "Next" }));
}

describe("CreateDataSourceWizardPage — setup step", () => {
  it("shows Source name field on the setup step", async () => {
    await navigateToScanSetup();
    expect(screen.getByLabelText("Source name")).toBeTruthy();
  });

  it("Next is disabled when source name is empty", async () => {
    await navigateToScanSetup();
    const btn = screen.getByRole("button", { name: "Next" }) as HTMLButtonElement;
    expect(btn.disabled).toBe(true);
  });
});

describe("CreateDataSourceWizardPage — basis step (UI-121)", () => {
  it("does not show Manual schema option", async () => {
    render(
      <MemoryRouter>
        <CreateDataSourceWizardPage />
      </MemoryRouter>,
    );
    await userEvent.click(screen.getByText("OPC UA"));
    await userEvent.click(screen.getByRole("button", { name: "Next" }));
    expect(screen.queryByText("Manual schema")).toBeNull();
  });
});

describe("CreateDataSourceWizardPage — scan setup validation", () => {
  it("enables Next when source name is filled", async () => {
    await navigateToScanSetup();
    await userEvent.type(screen.getByLabelText("Source name"), "Test source");

    const btn = screen.getByRole("button", { name: "Next" }) as HTMLButtonElement;
    expect(btn.disabled).toBe(false);
  });
});

// ─── validationMessage — local-mode credential skip (UI-116) ─────────────────

const baseScanForm: WizardFormState = {
  basis: "scan",
  importSelectedRecordingId: null,
  modbusAddressBase: "0",
  modbusUnitId: "1",
  name: "My Source",
  opcUaNamespaceStrategy: "normalize",
  opcUaSecurity: "None",
  protocol: "OPC UA",
  realDeviceEndpoint: "opc.tcp://host:4840",
  scanCredentialConfirmed: false,
  scanCredentialMode: "password",
  scanPassword: "",
  scanSecretRef: "",
  scanState: "idle",
  scanTestResult: "idle",
  scanUsername: "",
  scheduleEndEnabled: false,
  scheduleEnd: "",
  scheduleStartEnabled: false,
  scheduleStart: "",
  simulatorPort: "4840",
  schemaReviewNote: "",
  startCapture: true,
};

describe("validationMessage — local-mode credential skip", () => {
  it("returns null (no error) on setup step in local mode despite missing credentials", () => {
    const msg = validationMessage("setup", baseScanForm, "local");
    expect(msg).toBeNull();
  });

  it("returns credential error on setup step in shared mode", () => {
    const msg = validationMessage("setup", baseScanForm, "shared");
    expect(msg).toBeTruthy();
  });

  it("returns name error when name is empty regardless of mode", () => {
    const form = { ...baseScanForm, name: "" };
    expect(validationMessage("setup", form, "local")).toBeTruthy();
    expect(validationMessage("setup", form, "shared")).toBeTruthy();
  });
});

// ─── scanStepValidationMessage (UI-458) ──────────────────────────────────────

const knownNode: DiscoveredNodeResponse = {
  nodeId: "ns=2;s=Temp",
  parentId: null,
  path: "/Temp",
  name: "Temp",
  kind: "Variable",
  dataType: "Float",
  valueRank: 1,
  access: "READ",
  unit: null,
  description: null,
  unknownType: false,
};

const unknownNode: DiscoveredNodeResponse = {
  ...knownNode,
  nodeId: "ns=2;s=X",
  name: "X",
  dataType: null,
  unknownType: true,
};

describe("scanStepValidationMessage (UI-458)", () => {
  it("returns scanning message while scanning", () => {
    expect(scanStepValidationMessage("scanning", [], null)).toBe("Scanning in progress…");
  });

  it("returns error message on error state", () => {
    expect(scanStepValidationMessage("error", [], null)).toBe("Scan failed");
  });

  it("returns null when complete with no unknown nodes", () => {
    expect(
      scanStepValidationMessage("complete", [], { nodes: [knownNode], discoveredCount: 1, unknownCount: 0, truncated: false }),
    ).toBeNull();
  });

  it("returns null when partial with known nodes (partial is valid like complete)", () => {
    expect(
      scanStepValidationMessage("partial", [], { nodes: [knownNode], discoveredCount: 1, unknownCount: 0, truncated: false }),
    ).toBeNull();
  });

  it("returns unresolved message when unknown node has no type and no exclude", () => {
    const resolutions: TypeResolutionEntry[] = [
      { nodeId: unknownNode.nodeId, dataType: "", valueRank: 1, access: "READ", exclude: false },
    ];
    expect(
      scanStepValidationMessage("complete", resolutions, {
        nodes: [unknownNode],
        discoveredCount: 1,
        unknownCount: 1,
        truncated: false,
      }),
    ).toBe("Resolve all unknown node types to continue");
  });

  it("returns null when all unknown nodes are excluded", () => {
    const resolutions: TypeResolutionEntry[] = [
      { nodeId: unknownNode.nodeId, dataType: "", valueRank: 1, access: "READ", exclude: true },
    ];
    expect(
      scanStepValidationMessage("complete", resolutions, {
        nodes: [unknownNode],
        discoveredCount: 1,
        unknownCount: 1,
        truncated: false,
      }),
    ).toBeNull();
  });

  it("returns null when all unknown nodes have a dataType resolved", () => {
    const resolutions: TypeResolutionEntry[] = [
      { nodeId: unknownNode.nodeId, dataType: "FLOAT64", valueRank: 1, access: "READ", exclude: false },
    ];
    expect(
      scanStepValidationMessage("complete", resolutions, {
        nodes: [unknownNode],
        discoveredCount: 1,
        unknownCount: 1,
        truncated: false,
      }),
    ).toBeNull();
  });
});

// ─── Scan step rendering — integration tests (UI-458) ────────────────────────

function makeFetchResponse(status: number, body: unknown) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers({ "content-type": "application/json" }),
    json: () => Promise.resolve(body),
  };
}

async function navigateToScanStep() {
  // Render wizard and navigate through protocol → basis → setup → scan
  render(
    <MemoryRouter>
      <CreateDataSourceWizardPage />
    </MemoryRouter>,
  );

  // Protocol step
  await userEvent.click(screen.getByText("OPC UA"));
  await userEvent.click(screen.getByRole("button", { name: "Next" }));

  // Basis step — Real source
  await userEvent.click(screen.getByText("Real source"));
  await userEvent.click(screen.getByRole("button", { name: "Next" }));

  // Setup step — fill required fields
  await userEvent.type(screen.getByLabelText("Source name"), "Test source");

  // Next is now enabled — go to scan step
  await userEvent.click(screen.getByRole("button", { name: "Next" }));
}

// The integration tests for the scan step mock `apiFetch` at the module level
// rather than stubbing global fetch, so React internals are not affected.
// The scan effect fires on mount; we use waitFor to observe state transitions.

const { mockApiFetch } = vi.hoisted(() => ({ mockApiFetch: vi.fn() }));

vi.mock("../api", async () => {
  const actual = await vi.importActual<typeof import("../api")>("../api");
  return { ...actual, apiFetch: mockApiFetch };
});

// Default scan job stub: POST returns jobId, GET returns COMPLETE immediately.
function makeScanStart(jobId = "job-1") {
  return Promise.resolve({ jobId, status: "RUNNING" });
}

function makeScanResult(overrides: Partial<{
  jobId: string;
  status: string;
  discoveredCount: number;
  unknownCount: number;
  truncated: boolean;
  nodes: DiscoveredNodeResponse[];
  message: string | null;
}> = {}) {
  return Promise.resolve({
    jobId: "job-1",
    status: "COMPLETE",
    discoveredCount: 3,
    unknownCount: 0,
    truncated: false,
    nodes: [knownNode],
    message: null,
    ...overrides,
  });
}

// Helper to advance the FAKE setInterval and flush all pending async work.
// Uses advanceTimersByTimeAsync which properly handles async timer callbacks,
// then wraps in act() so React processes the resulting state updates.
async function advanceIntervalAndFlush(ms: number) {
  const { act: reactAct } = await import("@testing-library/react");
  await reactAct(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

describe("CreateDataSourceWizardPage — scan step (UI-458)", () => {
  // Fake ONLY setInterval/clearInterval for polling control.
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["setInterval", "clearInterval"] });
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it("shows scanning state immediately on entering the scan step", async () => {
    // POST returns right away; GET never resolves → component stays "scanning"
    mockApiFetch
      .mockImplementationOnce(() => Promise.resolve({ jobId: "job-1", status: "RUNNING" }))
      .mockImplementation(() => new Promise(() => { /* never resolves */ }));

    await navigateToScanStep();

    expect(screen.getByText(/Scanning…/i)).toBeTruthy();
  });

  it("transitions to complete state after polling returns COMPLETE", async () => {
    mockApiFetch
      .mockImplementationOnce(() => Promise.resolve({ jobId: "job-1", status: "RUNNING" }))
      .mockImplementationOnce(() =>
        Promise.resolve({
          jobId: "job-1",
          status: "COMPLETE",
          truncated: false,
          discoveredCount: 5,
          unknownCount: 0,
          message: null,
          nodes: [knownNode],
        }),
      );

    await navigateToScanStep();

    await advanceIntervalAndFlush(2000);

    expect(screen.getByText(/Discovered 5 nodes/i)).toBeTruthy();
  });

  it("shows error state and Retry button when scan poll returns ERROR", async () => {
    mockApiFetch
      .mockImplementationOnce(() => Promise.resolve({ jobId: "job-err", status: "RUNNING" }))
      .mockImplementationOnce(() =>
        Promise.resolve({
          jobId: "job-err",
          status: "ERROR",
          truncated: false,
          discoveredCount: 0,
          unknownCount: 0,
          message: "Connection refused",
          nodes: [],
        }),
      );

    await navigateToScanStep();
    await advanceIntervalAndFlush(2000);

    expect(screen.getByText(/Scan failed/i)).toBeTruthy();
    expect(screen.getByRole("button", { name: /Retry/i })).toBeTruthy();
  });

  it("Next is disabled while scanning", async () => {
    mockApiFetch
      .mockImplementationOnce(() => Promise.resolve({ jobId: "job-1", status: "RUNNING" }))
      .mockImplementation(() => new Promise(() => { /* never resolves */ }));

    await navigateToScanStep();

    // Interval hasn't fired yet → scanStatus is still "scanning"
    const btn = screen.getByRole("button", { name: "Next" }) as HTMLButtonElement;
    expect(btn.disabled).toBe(true);
  });

  it("Next is enabled when scan completes with no unknown types", async () => {
    mockApiFetch
      .mockImplementationOnce(() => Promise.resolve({ jobId: "job-1", status: "RUNNING" }))
      .mockImplementationOnce(() =>
        Promise.resolve({
          jobId: "job-1",
          status: "COMPLETE",
          truncated: false,
          discoveredCount: 3,
          unknownCount: 0,
          message: null,
          nodes: [knownNode],
        }),
      );

    await navigateToScanStep();
    await advanceIntervalAndFlush(2000);

    const btn = screen.getByRole("button", { name: "Next" }) as HTMLButtonElement;
    expect(btn.disabled).toBe(false);
  });

  it("Next is disabled when there are unresolved unknown types", async () => {
    mockApiFetch
      .mockImplementationOnce(() => Promise.resolve({ jobId: "job-1", status: "RUNNING" }))
      .mockImplementationOnce(() =>
        Promise.resolve({
          jobId: "job-1",
          status: "COMPLETE",
          truncated: false,
          discoveredCount: 1,
          unknownCount: 1,
          message: null,
          nodes: [unknownNode],
        }),
      );

    await navigateToScanStep();
    await advanceIntervalAndFlush(2000);

    expect(screen.getByText(/unknown types need resolution/i)).toBeTruthy();
    const btn = screen.getByRole("button", { name: "Next" }) as HTMLButtonElement;
    expect(btn.disabled).toBe(true);
  });

  it("resolving unknown type enables Next", async () => {
    mockApiFetch
      .mockImplementationOnce(() => Promise.resolve({ jobId: "job-1", status: "RUNNING" }))
      .mockImplementationOnce(() =>
        Promise.resolve({
          jobId: "job-1",
          status: "COMPLETE",
          truncated: false,
          discoveredCount: 1,
          unknownCount: 1,
          message: null,
          nodes: [unknownNode],
        }),
      );

    await navigateToScanStep();
    await advanceIntervalAndFlush(2000);

    expect(screen.getByLabelText(/Data type for/i)).toBeTruthy();

    // Before resolution: Next is disabled
    expect((screen.getByRole("button", { name: "Next" }) as HTMLButtonElement).disabled).toBe(true);

    // Resolve the unknown type
    await userEvent.selectOptions(screen.getByLabelText(/Data type for/i), "FLOAT64");

    // Now Next should be enabled
    expect((screen.getByRole("button", { name: "Next" }) as HTMLButtonElement).disabled).toBe(false);
  });

  it("calls scan/{jobId}/create endpoint on final Create source click (scan path)", async () => {
    mockApiFetch
      .mockImplementationOnce(() => Promise.resolve({ jobId: "job-2", status: "RUNNING" }))
      .mockImplementationOnce(() =>
        Promise.resolve({
          jobId: "job-2",
          status: "COMPLETE",
          truncated: false,
          discoveredCount: 2,
          unknownCount: 0,
          message: null,
          nodes: [knownNode],
        }),
      )
      .mockImplementationOnce(() => Promise.resolve({ id: "ds-created" }));

    await navigateToScanStep();
    await advanceIntervalAndFlush(2000);

    // Scan complete — Next should be enabled
    expect((screen.getByRole("button", { name: "Next" }) as HTMLButtonElement).disabled).toBe(false);

    // Navigate through recording, schedule, and review steps
    await userEvent.click(screen.getByRole("button", { name: "Next" }));
    await userEvent.click(screen.getByRole("button", { name: "Next" }));
    await userEvent.click(screen.getByRole("button", { name: "Next" }));

    // Click Create source
    await userEvent.click(screen.getByRole("button", { name: "Create source" }));

    // Verify the create call used the scan endpoint
    await waitFor(() => {
      const createCallArgs = mockApiFetch.mock.calls.find(
        (c: unknown[]) =>
          typeof c[0] === "string" && (c[0] as string).includes("/scan/job-2/create"),
      );
      expect(createCallArgs).toBeTruthy();
    });

    // Default startCapture=true → redirects to live capture page
    expect(mockNavigate).toHaveBeenCalledWith("/data-sources/ds-created/recording");
  });

  it("navigates to /data-sources/:id when Skip is chosen on recording step (startCapture=false)", async () => {
    mockApiFetch
      .mockImplementationOnce(() => Promise.resolve({ jobId: "job-3", status: "RUNNING" }))
      .mockImplementationOnce(() =>
        Promise.resolve({
          jobId: "job-3",
          status: "COMPLETE",
          truncated: false,
          discoveredCount: 1,
          unknownCount: 0,
          message: null,
          nodes: [knownNode],
        }),
      )
      .mockImplementationOnce(() => Promise.resolve({ id: "ds-skip" }));

    await navigateToScanStep();
    await advanceIntervalAndFlush(2000);

    // On recording step, click "Skip — just create the source"
    await userEvent.click(screen.getByRole("button", { name: "Next" }));
    await userEvent.click(screen.getByText(/Skip — just create the source/));

    // Continue through schedule and review
    await userEvent.click(screen.getByRole("button", { name: "Next" }));
    await userEvent.click(screen.getByRole("button", { name: "Next" }));

    await userEvent.click(screen.getByRole("button", { name: "Create source" }));

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith("/data-sources/ds-skip");
    });
  });
});
