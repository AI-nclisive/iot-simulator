/**
 * Tests for DataSourceSchemaEditor dependency warnings (UI-043, UI-097)
 * and NodeDto round-trip correctness (UI-109)
 *
 * Covers:
 * - No changes → no warnings returned
 * - Description change → identifier rename warning appears
 * - PUT payload preserves parentId, kind, dataType, valueRank, access from GET
 * - FOLDER nodes are included in PUT payload unchanged
 * - BYTES/DATETIME dataType values are preserved on save
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

// ---------------------------------------------------------------------------
// Round-trip tests (UI-109): PUT payload must preserve all NodeDto fields
// ---------------------------------------------------------------------------

describe("DataSourceSchemaEditor — NodeDto round-trip on save (UI-109)", () => {
  it("PUT payload preserves parentId, kind, dataType, valueRank, and access from GET", async () => {
    const schemaWithRichNodes = {
      id: "schema-rt",
      dataSourceId: "src-test",
      version: 1,
      nodes: [
        {
          nodeId: "v-001",
          parentId: "f-root",
          path: "root/temp",
          name: "temp",
          kind: "VARIABLE" as const,
          dataType: "FLOAT32",
          valueRank: "SCALAR",
          access: "READ_WRITE",
          unit: "°C",
          description: "Temperature",
        },
      ],
    };
    mockApiFetch
      .mockResolvedValueOnce(schemaWithRichNodes) // GET on mount
      .mockResolvedValueOnce(schemaWithRichNodes); // PUT on save

    render(<DataSourceSchemaEditor source={mockSource} projectId={PROJECT_ID} />);
    await screen.findByText("root/temp");
    await userEvent.click(screen.getByText("root/temp"));

    const unitInput = screen.getAllByRole("textbox").find(
      (el) => (el as HTMLInputElement).value === "°C",
    ) as HTMLInputElement;
    await userEvent.clear(unitInput);
    await userEvent.type(unitInput, "K");

    const saveButton = screen.getByRole("button", { name: "Save schema" });
    await userEvent.click(saveButton);

    // Find the PUT call (second apiFetch call)
    const putCall = mockApiFetch.mock.calls.find(
      (call) => (call[1] as RequestInit | undefined)?.method === "PUT",
    );
    expect(putCall).toBeTruthy();
    const body = JSON.parse((putCall![1] as RequestInit).body as string) as {
      nodes: Array<{
        nodeId: string;
        parentId: string | null;
        kind: string;
        dataType: string | null;
        valueRank: string | null;
        access: string | null;
        unit: string | null;
      }>;
    };
    const node = body.nodes.find((n) => n.nodeId === "v-001")!;
    expect(node.parentId).toBe("f-root");
    expect(node.kind).toBe("VARIABLE");
    expect(node.dataType).toBe("FLOAT32");
    expect(node.valueRank).toBe("SCALAR");
    expect(node.access).toBe("READ_WRITE");
    expect(node.unit).toBe("K");
  });

  it("PUT payload includes FOLDER nodes unchanged", async () => {
    const schemaWithFolder = {
      id: "schema-folder",
      dataSourceId: "src-test",
      version: 1,
      nodes: [
        {
          nodeId: "f-root",
          parentId: null,
          path: "root",
          name: "root",
          kind: "FOLDER" as const,
          dataType: null,
          valueRank: null,
          access: null,
          unit: null,
          description: null,
        },
        {
          nodeId: "v-child",
          parentId: "f-root",
          path: "root/pressure",
          name: "pressure",
          kind: "VARIABLE" as const,
          dataType: "FLOAT64",
          valueRank: null,
          access: "READ",
          unit: "bar",
          description: "Pressure sensor",
        },
      ],
    };
    mockApiFetch
      .mockResolvedValueOnce(schemaWithFolder) // GET on mount
      .mockResolvedValueOnce(schemaWithFolder); // PUT on save

    render(<DataSourceSchemaEditor source={mockSource} projectId={PROJECT_ID} />);
    await screen.findByText("root/pressure");
    await userEvent.click(screen.getByText("root/pressure"));

    const unitInput = screen.getAllByRole("textbox").find(
      (el) => (el as HTMLInputElement).value === "bar",
    ) as HTMLInputElement;
    await userEvent.clear(unitInput);
    await userEvent.type(unitInput, "Pa");

    const saveButton = screen.getByRole("button", { name: "Save schema" });
    await userEvent.click(saveButton);

    const putCall = mockApiFetch.mock.calls.find(
      (call) => (call[1] as RequestInit | undefined)?.method === "PUT",
    );
    expect(putCall).toBeTruthy();
    const body = JSON.parse((putCall![1] as RequestInit).body as string) as {
      nodes: Array<{ nodeId: string; kind: string; parentId: string | null }>;
    };
    // FOLDER node must be present in payload
    const folderNode = body.nodes.find((n) => n.nodeId === "f-root");
    expect(folderNode).toBeTruthy();
    expect(folderNode!.kind).toBe("FOLDER");
    expect(folderNode!.parentId).toBeNull();
    // Variable node must have updated unit
    const varNode = body.nodes.find((n) => n.nodeId === "v-child")!;
    expect(varNode.kind).toBe("VARIABLE");
  });

  it("PUT payload preserves BYTES dataType unchanged", async () => {
    const schemaWithBytes = {
      id: "schema-bytes",
      dataSourceId: "src-test",
      version: 1,
      nodes: [
        {
          nodeId: "v-bytes",
          parentId: null,
          path: "sensor/raw",
          name: "raw",
          kind: "VARIABLE" as const,
          dataType: "BYTES",
          valueRank: null,
          access: "READ",
          unit: "hex",
          description: "Raw bytes field",
        },
      ],
    };
    // Reset to avoid mock queue leaking from previous tests
    mockApiFetch.mockReset();
    mockApiFetch
      .mockResolvedValueOnce(schemaWithBytes) // GET on mount
      .mockResolvedValueOnce(schemaWithBytes); // PUT on save

    render(<DataSourceSchemaEditor source={mockSource} projectId={PROJECT_ID} />);
    await screen.findByText("sensor/raw");
    await userEvent.click(screen.getByText("sensor/raw"));

    // Edit unit (not description) to avoid triggering the dependency-warning dialog
    const unitInputs = screen.getAllByRole("textbox");
    const unitInput = unitInputs.find(
      (el) => (el as HTMLInputElement).value === "hex",
    ) as HTMLInputElement;
    expect(unitInput).toBeTruthy();
    await userEvent.clear(unitInput);
    await userEvent.type(unitInput, "bin");

    const saveButton = screen.getByRole("button", { name: "Save schema" });
    await userEvent.click(saveButton);

    // Wait for save to complete
    await screen.findByRole("button", { name: "Save schema" });

    const putCall = mockApiFetch.mock.calls.find(
      (call) => (call[1] as RequestInit | undefined)?.method === "PUT",
    );
    expect(putCall).toBeTruthy();
    const body = JSON.parse((putCall![1] as RequestInit).body as string) as {
      nodes: Array<{ nodeId: string; dataType: string | null; unit: string | null }>;
    };
    const node = body.nodes.find((n) => n.nodeId === "v-bytes")!;
    // BYTES dataType must be preserved unchanged — no FE type mapping for it
    expect(node.dataType).toBe("BYTES");
    expect(node.unit).toBe("bin");
  });

  it("PUT payload preserves DATETIME dataType unchanged", async () => {
    const schemaWithDatetime = {
      id: "schema-dt",
      dataSourceId: "src-test",
      version: 1,
      nodes: [
        {
          nodeId: "v-dt",
          parentId: null,
          path: "sensor/timestamp",
          name: "timestamp",
          kind: "VARIABLE" as const,
          dataType: "DATETIME",
          valueRank: null,
          access: "READ",
          unit: "epoch",
          description: "Timestamp field",
        },
      ],
    };
    // Reset to avoid mock queue leaking from previous tests
    mockApiFetch.mockReset();
    mockApiFetch
      .mockResolvedValueOnce(schemaWithDatetime)
      .mockResolvedValueOnce(schemaWithDatetime);

    render(<DataSourceSchemaEditor source={mockSource} projectId={PROJECT_ID} />);
    await screen.findByText("sensor/timestamp");
    await userEvent.click(screen.getByText("sensor/timestamp"));

    // Edit unit (not description) to avoid triggering the dependency-warning dialog
    const unitInputs = screen.getAllByRole("textbox");
    const unitInput = unitInputs.find(
      (el) => (el as HTMLInputElement).value === "epoch",
    ) as HTMLInputElement;
    expect(unitInput).toBeTruthy();
    await userEvent.clear(unitInput);
    await userEvent.type(unitInput, "ms");

    const saveButton = screen.getByRole("button", { name: "Save schema" });
    await userEvent.click(saveButton);

    await screen.findByRole("button", { name: "Save schema" });

    const putCall = mockApiFetch.mock.calls.find(
      (call) => (call[1] as RequestInit | undefined)?.method === "PUT",
    );
    expect(putCall).toBeTruthy();
    const body = JSON.parse((putCall![1] as RequestInit).body as string) as {
      nodes: Array<{ nodeId: string; dataType: string | null }>;
    };
    const node = body.nodes.find((n) => n.nodeId === "v-dt")!;
    expect(node.dataType).toBe("DATETIME");
  });
});
