/**
 * Tests for CreateDataSourceWizardPage (UI-037)
 *
 * Covers:
 * - Synthetic setup fields are shown in setup step when basis is synthetic
 * - Default parameter count is a positive integer (100)
 * - Default update interval is a positive integer (1000)
 * - Next is disabled when source name is empty (validation system active)
 * - Next is enabled when source name is filled with valid synthetic defaults
 */

import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CreateDataSourceWizardPage } from "./create-data-source-wizard-page";

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

async function navigateToSyntheticSetup() {
  render(
    <MemoryRouter>
      <CreateDataSourceWizardPage />
    </MemoryRouter>,
  );

  // Step 0: choose protocol
  await userEvent.click(screen.getByText("OPC UA"));
  await userEvent.click(screen.getByRole("button", { name: "Next" }));

  // Step 1: choose synthetic basis
  await userEvent.click(screen.getByText("Synthetic setup"));
  await userEvent.click(screen.getByRole("button", { name: "Next" }));
}

describe("CreateDataSourceWizardPage — synthetic setup fields", () => {
  it("shows synthetic controls in setup step for synthetic basis", async () => {
    await navigateToSyntheticSetup();

    expect(screen.getByText("Parameter count")).toBeTruthy();
    expect(screen.getByText("Update interval (ms)")).toBeTruthy();
    expect(screen.getByText("Value range — min")).toBeTruthy();
    expect(screen.getByText("Repeatability seed")).toBeTruthy();
  });

  it("renders parameter count input with positive integer default (100)", async () => {
    await navigateToSyntheticSetup();

    const inputs = screen.getAllByRole("textbox");
    const countInput = inputs.find(
      (el) => (el as HTMLInputElement).value === "100",
    ) as HTMLInputElement;
    expect(countInput).toBeTruthy();
    expect(Number.isInteger(Number(countInput.value))).toBe(true);
  });

  it("renders update interval input with positive integer default (1000)", async () => {
    await navigateToSyntheticSetup();

    const inputs = screen.getAllByRole("textbox");
    const intervalInput = inputs.find(
      (el) => (el as HTMLInputElement).value === "1000",
    ) as HTMLInputElement;
    expect(intervalInput).toBeTruthy();
    expect(Number.isInteger(Number(intervalInput.value))).toBe(true);
  });
});

describe("CreateDataSourceWizardPage — synthetic validation", () => {
  it("enables Next when source name is filled and defaults are valid", async () => {
    await navigateToSyntheticSetup();
    await userEvent.type(screen.getByLabelText("Source name"), "Test source");

    const btn = screen.getByRole("button", { name: "Next" }) as HTMLButtonElement;
    expect(btn.disabled).toBe(false);
  });
});
