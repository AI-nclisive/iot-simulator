/**
 * Tests for CreateDataSourceWizardPage (UI-037, UI-116)
 *
 * Covers:
 * - Source name field is shown on the manual schema setup step
 * - Next is disabled when source name is empty (validation system active)
 * - Next is enabled when source name is filled on manual schema path
 * - validationMessage skips credential validation in local mode (UI-116)
 * - validationMessage enforces credentials in shared mode
 */

import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  CreateDataSourceWizardPage,
  validationMessage,
  type WizardFormState,
} from "./create-data-source-wizard-page";

const { mockNavigate } = vi.hoisted(() => ({ mockNavigate: vi.fn() }));

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock("../shell/shell-store", () => ({
  useShellStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ accessMode: "local", sharedRole: "admin" }),
}));

vi.mock("../shell/data-sources-store", () => ({
  useDataSourcesStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({
      createDataSource: vi.fn(() => "src-new"),
      startDataSource: vi.fn(),
    }),
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

async function navigateToManualSetup() {
  render(
    <MemoryRouter>
      <CreateDataSourceWizardPage />
    </MemoryRouter>,
  );

  // Step 0: choose protocol
  await userEvent.click(screen.getByText("OPC UA"));
  await userEvent.click(screen.getByRole("button", { name: "Next" }));

  // Step 1: choose manual basis
  await userEvent.click(screen.getByText("Manual schema"));
  await userEvent.click(screen.getByRole("button", { name: "Next" }));
}

describe("CreateDataSourceWizardPage — setup step", () => {
  it("shows Source name field on the manual schema setup step", async () => {
    await navigateToManualSetup();
    expect(screen.getByLabelText("Source name")).toBeTruthy();
  });

  it("Next is disabled when source name is empty", async () => {
    await navigateToManualSetup();
    const btn = screen.getByRole("button", { name: "Next" }) as HTMLButtonElement;
    expect(btn.disabled).toBe(true);
  });
});

describe("CreateDataSourceWizardPage — manual schema validation", () => {
  it("enables Next when source name is filled", async () => {
    await navigateToManualSetup();
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
  runtimeBehavior: "stopped",
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
