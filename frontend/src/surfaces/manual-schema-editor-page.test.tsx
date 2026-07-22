/**
 * Tests for ManualSchemaEditorPage (UI-490).
 *
 * Covers:
 * - loadManualSchemaById is called on mount (always-fresh, per the store's ETag contract)
 * - editing the name enables Save and opens the save-in-place/save-as-new choice
 * - "Save in this schema" calls updateManualSchema
 * - "Save as a new schema" calls createManualSchema and navigates to the new schema
 */

import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ManualSchemaEditorPage, validateManualSchemaNodes } from "./manual-schema-editor-page";

const { mockNavigate } = vi.hoisted(() => ({ mockNavigate: vi.fn() }));

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return { ...actual, useNavigate: () => mockNavigate, useParams: () => ({ schemaId: "ms-1" }) };
});

const schema = {
  id: "ms-1",
  projectId: "proj-1",
  protocol: "OPC_UA",
  name: "Boiler layout",
  description: null,
  nodes: [
    { nodeId: "v1", parentId: null, path: "/v1", name: "Level", kind: "VARIABLE" as const,
      dataType: "FLOAT64", valueRank: "SCALAR", access: "READ", unit: null, description: null },
  ],
  version: 0,
};

const schemaWithFolder = {
  ...schema,
  nodes: [
    { nodeId: "f1", parentId: null, path: "/Reactor", name: "Reactor", kind: "FOLDER" as const,
      dataType: null, valueRank: null, access: null, unit: null, description: null },
    { nodeId: "v1", parentId: "f1", path: "/Reactor/Temp", name: "Temp", kind: "VARIABLE" as const,
      dataType: "FLOAT64", valueRank: "SCALAR", access: "READ", unit: null, description: null },
  ],
};

const { mockLoadManualSchemaById, mockUpdateManualSchema, mockCreateManualSchema } = vi.hoisted(() => ({
  mockLoadManualSchemaById: vi.fn(),
  mockUpdateManualSchema: vi.fn(),
  mockCreateManualSchema: vi.fn(),
}));

vi.mock("../shell/shell-store", () => ({
  useShellStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ accessMode: "local", sharedRole: "admin", currentProjectId: "proj-1" }),
}));

vi.mock("../shell/manual-schemas-store", () => ({
  useManualSchemasStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({
      loadManualSchemaById: mockLoadManualSchemaById,
      updateManualSchema: mockUpdateManualSchema,
      createManualSchema: mockCreateManualSchema,
    }),
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
  return render(
    <MemoryRouter>
      <ManualSchemaEditorPage />
    </MemoryRouter>,
  );
}

describe("ManualSchemaEditorPage (UI-490)", () => {
  it("loads the schema on mount and renders its fields", async () => {
    mockLoadManualSchemaById.mockResolvedValueOnce(schema);
    renderPage();

    expect(mockLoadManualSchemaById).toHaveBeenCalledWith("proj-1", "ms-1");
    await waitFor(() => {
      expect(screen.getByDisplayValue("Boiler layout")).not.toBeNull();
    });
    expect(screen.getByText("Level")).not.toBeNull();
  });

  it("saves in place when the user picks 'Save in this schema'", async () => {
    mockLoadManualSchemaById.mockResolvedValueOnce(schema);
    mockUpdateManualSchema.mockResolvedValueOnce({ ...schema, name: "Renamed", version: 1 });
    renderPage();

    await waitFor(() => screen.getByDisplayValue("Boiler layout"));
    fireEvent.change(screen.getByDisplayValue("Boiler layout"), { target: { value: "Renamed" } });

    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    fireEvent.click(screen.getByLabelText(/Save in this schema/));
    fireEvent.click(screen.getAllByRole("button", { name: "Save" })[1]);

    await waitFor(() => {
      expect(mockUpdateManualSchema).toHaveBeenCalledWith(
        "proj-1",
        "ms-1",
        expect.objectContaining({ name: "Renamed" }),
      );
    });
  });

  it("saves as a new schema and navigates to it", async () => {
    mockLoadManualSchemaById.mockResolvedValueOnce(schema);
    mockCreateManualSchema.mockResolvedValueOnce({ ...schema, id: "ms-2", name: "Renamed (copy)" });
    renderPage();

    await waitFor(() => screen.getByDisplayValue("Boiler layout"));
    fireEvent.change(screen.getByDisplayValue("Boiler layout"), { target: { value: "Renamed" } });

    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    fireEvent.click(screen.getByLabelText(/Save as a new schema/));
    fireEvent.click(screen.getAllByRole("button", { name: "Save" })[1]);

    await waitFor(() => {
      expect(mockCreateManualSchema).toHaveBeenCalledWith(
        "proj-1",
        expect.objectContaining({ protocol: "OPC_UA" }),
      );
    });
    expect(mockNavigate).toHaveBeenCalledWith("/manual-schemas/ms-2");
  });

  it("renaming a folder cascades the new path prefix to its descendants", async () => {
    mockLoadManualSchemaById.mockResolvedValueOnce(schemaWithFolder);
    mockUpdateManualSchema.mockResolvedValueOnce({ ...schemaWithFolder, version: 1 });
    renderPage();

    await waitFor(() => screen.getByText("Reactor"));
    fireEvent.click(screen.getByText("Reactor"));
    const nameInputs = screen.getAllByDisplayValue("Reactor");
    // The schema's own name field also reads "Boiler layout", not "Reactor" — only the
    // selected-node detail panel's Name field shows "Reactor".
    fireEvent.change(nameInputs[nameInputs.length - 1], { target: { value: "Vessel" } });

    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    fireEvent.click(screen.getByLabelText(/Save in this schema/));
    fireEvent.click(screen.getAllByRole("button", { name: "Save" })[1]);

    await waitFor(() => {
      expect(mockUpdateManualSchema).toHaveBeenCalledWith(
        "proj-1",
        "ms-1",
        expect.objectContaining({
          nodes: expect.arrayContaining([
            expect.objectContaining({ nodeId: "f1", name: "Vessel", path: "/Vessel" }),
            expect.objectContaining({ nodeId: "v1", path: "/Vessel/Temp" }),
          ]),
        }),
      );
    });
  });

  it("adds a typed variable from the parameter catalog into the selected folder", async () => {
    mockLoadManualSchemaById.mockResolvedValueOnce(schemaWithFolder);
    renderPage();

    await waitFor(() => screen.getByText("Reactor"));
    fireEvent.click(screen.getByText("Reactor"));
    fireEvent.click(screen.getByRole("button", { name: /Choose from parameter catalog/i }));
    fireEvent.click(screen.getByRole("button", { name: /Temperature.*Process temperature/i }));

    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    fireEvent.click(screen.getByLabelText(/Save in this schema/));
    fireEvent.click(screen.getAllByRole("button", { name: "Save" })[1]);

    await waitFor(() => {
      expect(mockUpdateManualSchema).toHaveBeenCalledWith(
        "proj-1",
        "ms-1",
        expect.objectContaining({
          nodes: expect.arrayContaining([
            expect.objectContaining({ name: "Temperature", parentId: "f1", dataType: "FLOAT64", unit: "°C" }),
          ]),
        }),
      );
    });
  });

  it("filters the catalog and inserts a reusable simulation structure", async () => {
    mockLoadManualSchemaById.mockResolvedValueOnce(schemaWithFolder);
    renderPage();

    await waitFor(() => screen.getByText("Reactor"));
    fireEvent.click(screen.getByText("Reactor"));
    fireEvent.click(screen.getByRole("button", { name: /Choose from parameter catalog/i }));
    fireEvent.change(screen.getByLabelText("Search parameter catalog"), { target: { value: "simulation" } });
    fireEvent.click(screen.getByRole("button", { name: /Simulation signals.*folder with common generated signals/i }));

    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    fireEvent.click(screen.getByLabelText(/Save in this schema/));
    fireEvent.click(screen.getAllByRole("button", { name: "Save" })[1]);

    await waitFor(() => {
      expect(mockUpdateManualSchema).toHaveBeenCalledWith(
        "proj-1",
        "ms-1",
        expect.objectContaining({
          nodes: expect.arrayContaining([
            expect.objectContaining({ name: "Simulation signals", parentId: "f1", kind: "FOLDER" }),
            expect.objectContaining({ name: "Counter", dataType: "FLOAT64" }),
            expect.objectContaining({ name: "Sinusoid", dataType: "FLOAT64" }),
          ]),
        }),
      );
    });
  });

  it("lets a new node be placed under any folder, not only the selected tree node", async () => {
    mockLoadManualSchemaById.mockResolvedValueOnce(schemaWithFolder);
    renderPage();

    await waitFor(() => screen.getByText("Reactor"));
    fireEvent.click(screen.getByRole("button", { name: "Add variable" }));
    fireEvent.change(screen.getByLabelText("Parent folder for new node"), { target: { value: "f1" } });
    fireEvent.change(screen.getAllByLabelText("Name").at(-1)!, { target: { value: "Pressure" } });
    fireEvent.click(screen.getByRole("button", { name: "Add" }));

    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    fireEvent.click(screen.getByLabelText(/Save in this schema/));
    fireEvent.click(screen.getAllByRole("button", { name: "Save" })[1]);

    await waitFor(() => {
      expect(mockUpdateManualSchema).toHaveBeenCalledWith(
        "proj-1",
        "ms-1",
        expect.objectContaining({
          nodes: expect.arrayContaining([
            expect.objectContaining({ name: "Pressure", parentId: "f1", path: "/Reactor/Pressure" }),
          ]),
        }),
      );
    });
  });

  it("fills a new variable from the suggested parameter catalog", async () => {
    mockLoadManualSchemaById.mockResolvedValueOnce(schemaWithFolder);
    renderPage();

    await waitFor(() => screen.getByText("Reactor"));
    fireEvent.click(screen.getByRole("button", { name: "Add variable" }));
    fireEvent.change(screen.getByLabelText("Suggested parameter"), { target: { value: "Temperature" } });

    expect(screen.getAllByDisplayValue("Temperature").length).toBeGreaterThan(0);
    expect(screen.getByDisplayValue("°C")).not.toBeNull();
    expect(screen.getByDisplayValue("Process temperature")).not.toBeNull();
  });

  it("adds several typed sibling variables from editable rows", async () => {
    mockLoadManualSchemaById.mockResolvedValueOnce(schemaWithFolder);
    renderPage();

    await waitFor(() => screen.getByText("Reactor"));
    fireEvent.click(screen.getByRole("button", { name: "Add multiple variables" }));
    fireEvent.change(screen.getByLabelText("Parent folder for multiple variables"), { target: { value: "f1" } });
    fireEvent.click(screen.getByRole("button", { name: "+ Add row" }));
    fireEvent.click(screen.getByRole("button", { name: "+ Add row" }));
    fireEvent.change(screen.getByLabelText("Variable 1 name"), { target: { value: "Pressure" } });
    fireEvent.change(screen.getByLabelText("Variable 2 name"), { target: { value: "Enabled" } });
    fireEvent.change(screen.getByLabelText("Variable 2 type"), { target: { value: "BOOL" } });
    fireEvent.click(screen.getByRole("button", { name: "Add variables" }));

    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    fireEvent.click(screen.getByLabelText(/Save in this schema/));
    fireEvent.click(screen.getAllByRole("button", { name: "Save" })[1]);

    await waitFor(() => {
      expect(mockUpdateManualSchema).toHaveBeenCalledWith(
        "proj-1",
        "ms-1",
        expect.objectContaining({
          nodes: expect.arrayContaining([
            expect.objectContaining({ name: "Pressure", parentId: "f1", dataType: "FLOAT64" }),
            expect.objectContaining({ name: "Enabled", parentId: "f1", dataType: "BOOL" }),
          ]),
        }),
      );
    });
  });

  it("edits a variable's value shape and client access", async () => {
    mockLoadManualSchemaById.mockResolvedValueOnce(schema);
    mockUpdateManualSchema.mockResolvedValueOnce(schema);
    renderPage();

    await waitFor(() => screen.getByText("Level"));
    fireEvent.click(screen.getByText("Level"));
    fireEvent.change(screen.getByLabelText("Value shape"), { target: { value: "ARRAY" } });
    fireEvent.change(screen.getByLabelText("Client access"), { target: { value: "READ_WRITE" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    fireEvent.click(screen.getByLabelText(/Save in this schema/));
    fireEvent.click(screen.getAllByRole("button", { name: "Save" })[1]);

    await waitFor(() => expect(mockUpdateManualSchema).toHaveBeenCalledWith(
      "proj-1", "ms-1", expect.objectContaining({ nodes: expect.arrayContaining([
        expect.objectContaining({ nodeId: "v1", valueRank: "ARRAY", access: "READ_WRITE" }),
      ]) }),
    ));
  });
});

describe("validateManualSchemaNodes", () => {
  it("allows case-sensitive sibling browse names", () => {
    const issues = validateManualSchemaNodes([
      { nodeId: "upper", parentId: null, path: "/Temp", name: "Temp", kind: "VARIABLE" as const, dataType: "FLOAT64", valueRank: "SCALAR", access: "READ", unit: null, description: null },
      { nodeId: "lower", parentId: null, path: "/temp", name: "temp", kind: "VARIABLE" as const, dataType: "FLOAT64", valueRank: "SCALAR", access: "READ", unit: null, description: null },
    ]);

    expect(issues).toEqual([]);
  });

  it("reports duplicate names and a node under a variable", () => {
    const issues = validateManualSchemaNodes([
      { nodeId: "parent", parentId: null, path: "/Parent", name: "Parent", kind: "VARIABLE" as const, dataType: "FLOAT64", valueRank: "SCALAR", access: "READ", unit: null, description: null },
      { nodeId: "child", parentId: "parent", path: "/Parent/Bad", name: "Bad/Name", kind: "VARIABLE" as const, dataType: "FLOAT64", valueRank: "SCALAR", access: "READ", unit: null, description: null },
      { nodeId: "duplicate", parentId: "parent", path: "/Parent/Bad2", name: "Bad/Name", kind: "VARIABLE" as const, dataType: "FLOAT64", valueRank: "SCALAR", access: "READ", unit: null, description: null },
    ]);
    expect(issues.map((issue) => issue.message)).toEqual(expect.arrayContaining([
      "A browse name cannot contain a slash or backslash.",
      "Only a folder can contain another node.",
      "Sibling nodes must have unique browse names.",
    ]));
  });

  it("rejects a backslash in a browse name", () => {
    const issues = validateManualSchemaNodes([
      { nodeId: "backslash", parentId: null, path: "/Bad", name: "Bad\\Name", kind: "VARIABLE" as const, dataType: "FLOAT64", valueRank: "SCALAR", access: "READ", unit: null, description: null },
    ]);

    expect(issues).toEqual([{ nodeId: "backslash", message: "A browse name cannot contain a slash or backslash." }]);
  });
});
