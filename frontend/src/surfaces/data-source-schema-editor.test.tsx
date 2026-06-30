/**
 * Tests for DataSourceSchemaEditor dependency warnings (UI-043, UI-097)
 *
 * Covers:
 * - No changes → no warnings returned
 * - Description change → identifier rename warning appears
 */

import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi, type MockedFunction } from "vitest";
import { detectDependencyWarnings, DataSourceSchemaEditor } from "./data-source-schema-editor";
import type { SchemaParameter } from "./mock-schema-parameters";
import type { DataSourceRow } from "./mock-data-sources";
import { apiFetch } from "../api";

vi.mock("../api", () => ({
  apiFetch: vi.fn(),
  ApiError: class ApiError extends Error {
    constructor(
      public readonly status: number,
      public readonly title: string,
      public readonly detail: string | undefined,
      public readonly type: string | undefined,
    ) {
      super(title);
      this.name = "ApiError";
    }
  },
  mapProtocol: vi.fn(),
  mapRuntimeStateToStatus: vi.fn(),
  mapRuntimeStateToHealth: vi.fn(),
  mapDataType: vi.fn((dt: string) => {
    if (dt === "FLOAT32" || dt === "FLOAT64") return "float";
    if (dt === "BOOL") return "bool";
    if (dt === "STRING") return "string";
    return "int";
  }),
}));

const mockApiFetch = apiFetch as MockedFunction<typeof apiFetch>;

// Mock schema response matching some parameters expected by the tests
const mockSchemaResponse = {
  id: "schema-1",
  dataSourceId: "src-test",
  version: 1,
  nodes: [
    {
      nodeId: "p-001",
      parentId: null,
      path: "oven/zone[1]/temp",
      name: "zone1.temp",
      kind: "VARIABLE" as const,
      dataType: "FLOAT32",
      valueRank: null,
      access: null,
      unit: "°C",
      description: "Zone 1 temperature sensor",
    },
    {
      nodeId: "p-folder",
      parentId: null,
      path: "oven",
      name: "oven",
      kind: "FOLDER" as const,
      dataType: null,
      valueRank: null,
      access: null,
      unit: null,
      description: null,
    },
  ],
};

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

beforeEach(() => {
  // Return schema by default; individual tests can override
  mockApiFetch.mockResolvedValue(mockSchemaResponse);
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

const PROJECT_ID = "proj-test";

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
    render(<DataSourceSchemaEditor source={mockSource} projectId={PROJECT_ID} />);

    // Wait for schema to load
    await screen.findByText("oven/zone[1]/temp");
    await userEvent.click(screen.getByText("oven/zone[1]/temp"));

    // No Dependency impact section without changes
    expect(screen.queryByText("Dependency impact")).toBeNull();
  });

  it("shows identifier warning after changing description", async () => {
    render(<DataSourceSchemaEditor source={mockSource} projectId={PROJECT_ID} />);

    await screen.findByText("oven/zone[1]/temp");
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
    render(<DataSourceSchemaEditor source={mockSource} projectId={PROJECT_ID} onUnsavedChanges={onUnsavedChanges} />);

    await screen.findByText("oven/zone[1]/temp");
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
    render(<DataSourceSchemaEditor source={mockSource} projectId={PROJECT_ID} onUnsavedChanges={onUnsavedChanges} />);

    await screen.findByText("oven/zone[1]/temp");
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
      render(<DataSourceSchemaEditor source={mockSource} projectId={PROJECT_ID} />),
    ).not.toThrow();
  });
});

describe("DataSourceSchemaEditor — ConfirmationDialog flow when saving with warnings", () => {
  it("clicking Save when dependency warnings are active opens the ConfirmationDialog", async () => {
    render(<DataSourceSchemaEditor source={mockSource} projectId={PROJECT_ID} />);

    await screen.findByText("oven/zone[1]/temp");
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
    // PUT schema returns the schema response
    mockApiFetch
      .mockResolvedValueOnce(mockSchemaResponse) // GET schema on mount
      .mockResolvedValueOnce(mockSchemaResponse); // PUT schema on save
    render(<DataSourceSchemaEditor source={mockSource} projectId={PROJECT_ID} />);

    await screen.findByText("oven/zone[1]/temp");
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
    render(<DataSourceSchemaEditor source={mockSource} projectId={PROJECT_ID} />);

    await screen.findByText("oven/zone[1]/temp");
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
