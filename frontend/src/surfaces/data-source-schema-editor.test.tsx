/**
 * Tests for DataSourceSchemaEditor dependency warnings (UI-043)
 *
 * Covers:
 * - No changes → no warnings returned
 * - Description change → identifier rename warning appears
 */

import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { detectDependencyWarnings, DataSourceSchemaEditor } from "./data-source-schema-editor";
import type { SchemaParameter } from "./mock-schema-parameters";
import type { DataSourceRow } from "./mock-data-sources";

// ---------------------------------------------------------------------------
// Mock workspace lock so the editor is always unlocked in tests
// ---------------------------------------------------------------------------
vi.mock("../shell/mock-workspace", () => ({
  mockSourceLock: "unlocked",
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeParam(overrides: Partial<SchemaParameter> = {}): SchemaParameter {
  return {
    id: "p-test",
    name: "test.param",
    path: "test/param",
    type: "float",
    unit: "°C",
    description: "A test parameter",
    ...overrides,
  };
}

const mockSource: DataSourceRow = {
  id: "src-test",
  name: "Test Source",
  protocol: "OPC UA",
  endpoint: "opc.tcp://localhost:4840",
  parameterCount: 5,
  status: "Active",
  health: "Healthy",
};

// ---------------------------------------------------------------------------
// Unit tests for detectDependencyWarnings helper
// ---------------------------------------------------------------------------

describe("detectDependencyWarnings — no changes", () => {
  it("returns no warnings when buffer matches original exactly", () => {
    const param = makeParam();
    const original = { description: "A test parameter", unit: "°C" };
    const buffer = { ...original };
    const warnings = detectDependencyWarnings(param, buffer, original);
    expect(warnings).toHaveLength(0);
  });
});

describe("detectDependencyWarnings — description (identifier rename)", () => {
  it("returns identifier warning when description is changed", () => {
    const param = makeParam();
    const original = { description: "Original description", unit: "°C" };
    const buffer = { ...original, description: "Changed description" };
    const warnings = detectDependencyWarnings(param, buffer, original);
    expect(warnings.some((w) => w.includes("identifier"))).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// Integration tests via rendered component
// ---------------------------------------------------------------------------

describe("DataSourceSchemaEditor — dependency warnings in UI", () => {
  it("shows no warnings when no changes are made", async () => {
    render(<DataSourceSchemaEditor source={mockSource} />);

    await userEvent.click(screen.getByText("oven/zone[1]/temp"));

    // No Dependency impact section without changes
    expect(screen.queryByText("Dependency impact")).toBeNull();
  });

  it("shows identifier warning after changing description", async () => {
    render(<DataSourceSchemaEditor source={mockSource} />);

    await userEvent.click(screen.getByText("oven/zone[1]/temp"));

    const descriptionInputs = screen.getAllByRole("textbox");
    const descriptionInput = descriptionInputs.find(
      (el) => (el as HTMLInputElement).value === "Zone 1 temperature sensor",
    ) as HTMLInputElement;
    expect(descriptionInput).toBeTruthy();

    await userEvent.clear(descriptionInput);
    await userEvent.type(descriptionInput, "Changed description");

    expect(screen.getByText("Dependency impact")).toBeTruthy();
    expect(screen.getByText(/identifier/)).toBeTruthy();
  });
});

describe("DataSourceSchemaEditor — onUnsavedChanges callback", () => {
  it("calls onUnsavedChanges(true) when a field is edited", async () => {
    const onUnsavedChanges = vi.fn();
    render(<DataSourceSchemaEditor source={mockSource} onUnsavedChanges={onUnsavedChanges} />);

    await userEvent.click(screen.getByText("oven/zone[1]/temp"));
    const unitInputs = screen.getAllByRole("textbox");
    const unitInput = unitInputs.find(
      (el) => (el as HTMLInputElement).value === "°C",
    ) as HTMLInputElement;

    await userEvent.clear(unitInput);
    await userEvent.type(unitInput, "K");

    expect(onUnsavedChanges).toHaveBeenCalledWith(true);
  });

  it("calls onUnsavedChanges(false) after discarding changes", async () => {
    const onUnsavedChanges = vi.fn();
    render(<DataSourceSchemaEditor source={mockSource} onUnsavedChanges={onUnsavedChanges} />);

    await userEvent.click(screen.getByText("oven/zone[1]/temp"));
    const unitInputs = screen.getAllByRole("textbox");
    const unitInput = unitInputs.find(
      (el) => (el as HTMLInputElement).value === "°C",
    ) as HTMLInputElement;
    await userEvent.clear(unitInput);
    await userEvent.type(unitInput, "K");

    expect(onUnsavedChanges).toHaveBeenCalledWith(true);

    const discardButton = screen.getByRole("button", { name: "Discard changes" });
    await userEvent.click(discardButton);

    expect(onUnsavedChanges).toHaveBeenCalledWith(false);
  });

  it("does not require onUnsavedChanges to be provided", async () => {
    expect(() =>
      render(<DataSourceSchemaEditor source={mockSource} />),
    ).not.toThrow();
  });
});

describe("DataSourceSchemaEditor — ConfirmationDialog flow when saving with warnings", () => {
  it("clicking Save when dependency warnings are active opens the ConfirmationDialog", async () => {
    render(<DataSourceSchemaEditor source={mockSource} />);

    // Select zone1.temp and change description to trigger warning
    await userEvent.click(screen.getByText("oven/zone[1]/temp"));

    const descriptionInputs = screen.getAllByRole("textbox");
    const descriptionInput = descriptionInputs.find(
      (el) => (el as HTMLInputElement).value === "Zone 1 temperature sensor",
    ) as HTMLInputElement;
    await userEvent.clear(descriptionInput);
    await userEvent.type(descriptionInput, "Changed");

    // Dependency impact warning should be visible
    expect(screen.getByText("Dependency impact")).toBeTruthy();

    // Click Save schema
    const saveButton = screen.getByRole("button", { name: "Save schema" });
    await userEvent.click(saveButton);

    // ConfirmationDialog should appear with its title
    expect(screen.getByText("Save with dependency impact")).toBeTruthy();
  });

  it("confirming the ConfirmationDialog proceeds to save and closes the dialog", async () => {
    render(<DataSourceSchemaEditor source={mockSource} />);

    await userEvent.click(screen.getByText("oven/zone[1]/temp"));
    const descriptionInputs = screen.getAllByRole("textbox");
    const descriptionInput = descriptionInputs.find(
      (el) => (el as HTMLInputElement).value === "Zone 1 temperature sensor",
    ) as HTMLInputElement;
    await userEvent.clear(descriptionInput);
    await userEvent.type(descriptionInput, "Changed");

    const saveButton = screen.getByRole("button", { name: "Save schema" });
    await userEvent.click(saveButton);
    expect(screen.getByText("Save with dependency impact")).toBeTruthy();

    const confirmButton = screen.getByRole("button", { name: "Confirm" });
    await userEvent.click(confirmButton);

    expect(screen.queryByText("Save with dependency impact")).toBeNull();
  });

  it("cancelling the ConfirmationDialog does NOT save and leaves the form dirty", async () => {
    render(<DataSourceSchemaEditor source={mockSource} />);

    await userEvent.click(screen.getByText("oven/zone[1]/temp"));
    const descriptionInputs = screen.getAllByRole("textbox");
    const descriptionInput = descriptionInputs.find(
      (el) => (el as HTMLInputElement).value === "Zone 1 temperature sensor",
    ) as HTMLInputElement;
    await userEvent.clear(descriptionInput);
    await userEvent.type(descriptionInput, "Changed");

    const saveButton = screen.getByRole("button", { name: "Save schema" });
    await userEvent.click(saveButton);
    expect(screen.getByText("Save with dependency impact")).toBeTruthy();

    const cancelButton = screen.getByRole("button", { name: "Cancel" });
    await userEvent.click(cancelButton);

    expect(screen.queryByText("Save with dependency impact")).toBeNull();
    expect(screen.getByText("Unsaved changes")).toBeTruthy();
    expect(screen.getByText("Dependency impact")).toBeTruthy();
  });
});
