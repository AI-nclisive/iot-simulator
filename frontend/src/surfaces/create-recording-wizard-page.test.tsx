/**
 * Tests for CreateRecordingWizardPage (UI-115)
 *
 * Covers:
 * - Step 1 (Connection): renders protocol options, Next disabled without protocol
 * - Step 1 → Step 2 navigation when protocol + endpoint filled
 * - Step 2 (Scan type): Next disabled until scan type selected
 * - Step 3 (Schedule): renders immediate-start notice by default
 * - Step 4 (Review): shows Create recording button
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

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function renderPage() {
  render(
    <MemoryRouter>
      <CreateRecordingWizardPage />
    </MemoryRouter>,
  );
}

describe("CreateRecordingWizardPage — step 1 (Connection)", () => {
  it("renders protocol option buttons", () => {
    renderPage();
    expect(screen.getByText("OPC UA")).toBeTruthy();
    expect(screen.getByText("Modbus TCP")).toBeTruthy();
  });

  it("Next is disabled when no protocol selected", () => {
    renderPage();
    const btn = screen.getByRole("button", { name: "Next" }) as HTMLButtonElement;
    expect(btn.disabled).toBe(true);
  });

  it("Next remains disabled when protocol selected but endpoint empty", async () => {
    renderPage();
    await userEvent.click(screen.getByText("OPC UA"));
    const btn = screen.getByRole("button", { name: "Next" }) as HTMLButtonElement;
    expect(btn.disabled).toBe(true);
  });

  it("Next enabled when protocol and endpoint filled", async () => {
    renderPage();
    await userEvent.click(screen.getByText("OPC UA"));
    await userEvent.type(screen.getByPlaceholderText(/opc.tcp/i), "opc.tcp://host:4840");
    const btn = screen.getByRole("button", { name: "Next" }) as HTMLButtonElement;
    expect(btn.disabled).toBe(false);
  });
});

describe("CreateRecordingWizardPage — step 2 (Scan type)", () => {
  async function navigateToScanType() {
    renderPage();
    await userEvent.click(screen.getByText("OPC UA"));
    await userEvent.type(screen.getByPlaceholderText(/opc.tcp/i), "opc.tcp://host:4840");
    await userEvent.click(screen.getByRole("button", { name: "Next" }));
  }

  it("renders scan type options after advancing from connection step", async () => {
    await navigateToScanType();
    expect(screen.getByText("Scan schema only")).toBeTruthy();
    expect(screen.getByText("Scan schema + data")).toBeTruthy();
  });

  it("Next is disabled until scan type is chosen", async () => {
    await navigateToScanType();
    const btn = screen.getByRole("button", { name: "Next" }) as HTMLButtonElement;
    expect(btn.disabled).toBe(true);
  });

  it("Next enabled after scan type selected", async () => {
    await navigateToScanType();
    await userEvent.click(screen.getByText("Scan schema only"));
    const btn = screen.getByRole("button", { name: "Next" }) as HTMLButtonElement;
    expect(btn.disabled).toBe(false);
  });
});

describe("CreateRecordingWizardPage — step 3 (Schedule)", () => {
  async function navigateToSchedule() {
    renderPage();
    await userEvent.click(screen.getByText("OPC UA"));
    await userEvent.type(screen.getByPlaceholderText(/opc.tcp/i), "opc.tcp://host:4840");
    await userEvent.click(screen.getByRole("button", { name: "Next" }));
    await userEvent.click(screen.getByText("Scan schema only"));
    await userEvent.click(screen.getByRole("button", { name: "Next" }));
  }

  it("shows immediate-start notice by default", async () => {
    await navigateToSchedule();
    expect(screen.getByText(/start immediately and run without a time limit/i)).toBeTruthy();
  });
});

describe("CreateRecordingWizardPage — step 4 (Review)", () => {
  async function navigateToReview() {
    renderPage();
    await userEvent.click(screen.getByText("OPC UA"));
    await userEvent.type(screen.getByPlaceholderText(/opc.tcp/i), "opc.tcp://host:4840");
    await userEvent.click(screen.getByRole("button", { name: "Next" }));
    await userEvent.click(screen.getByText("Scan schema only"));
    await userEvent.click(screen.getByRole("button", { name: "Next" }));
    await userEvent.click(screen.getByRole("button", { name: "Next" }));
  }

  it("renders Create recording submit button on review step", async () => {
    await navigateToReview();
    expect(screen.getByRole("button", { name: "Create recording" })).toBeTruthy();
  });
});
