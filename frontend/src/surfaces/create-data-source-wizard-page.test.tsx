/**
 * Tests for CreateDataSourceWizardPage (UI-036)
 *
 * Covers:
 * - Schema step shows "Schema Editor handoff" block for manual basis
 * - Creating a source with manual basis navigates to ?tab=schema
 */

import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CreateDataSourceWizardPage } from "./create-data-source-wizard-page";

const { mockNavigate, mockCreateDataSource } = vi.hoisted(() => ({
  mockNavigate: vi.fn(),
  mockCreateDataSource: vi.fn(() => "src-new"),
}));

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
      createDataSource: mockCreateDataSource,
      startDataSource: vi.fn(),
    }),
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function renderWizard() {
  return render(
    <MemoryRouter>
      <CreateDataSourceWizardPage />
    </MemoryRouter>,
  );
}

async function navigateToSchemaStep() {
  const user = userEvent.setup();
  renderWizard();

  // Step 0: choose protocol
  await user.click(screen.getByText("OPC UA"));
  await user.click(screen.getByRole("button", { name: "Next" }));

  // Step 1: choose manual basis
  await user.click(screen.getByText("Manual schema"));
  await user.click(screen.getByRole("button", { name: "Next" }));

  // Step 2: fill source name
  await user.type(screen.getByRole("textbox"), "Test source");
  await user.click(screen.getByRole("button", { name: "Next" }));

  return user;
}

describe("CreateDataSourceWizardPage — manual schema step", () => {
  it("shows Schema Editor handoff block in schema step for manual basis", async () => {
    await navigateToSchemaStep();
    expect(screen.getByText("Schema Editor handoff")).toBeTruthy();
  });
});

describe("CreateDataSourceWizardPage — manual source redirect", () => {
  it("navigates to ?tab=schema after creating a source with manual basis", async () => {
    const user = await navigateToSchemaStep();

    // Step 3: schema step — click Next
    await user.click(screen.getByRole("button", { name: "Next" }));

    // Step 4: runtime step — click Next
    await user.click(screen.getByRole("button", { name: "Next" }));

    // Step 5: review — click Create source
    await user.click(screen.getByRole("button", { name: "Create source" }));

    expect(mockNavigate).toHaveBeenCalledWith(
      expect.stringContaining("?tab=schema"),
    );
  });
});
