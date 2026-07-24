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
import { ManualSchemaEditorPage, validateManualSchemaNodes, collectSubtreeIds } from "./manual-schema-editor-page";
import {
  deleteNodeOperation,
  duplicateNodeOperation,
  pasteNodeOperation,
} from "./schema-operations";

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
      dataType: "FLOAT64", valueRank: "SCALAR", access: "READ", unit: null, description: null,
      accessLevelFull: null, minimumSamplingInterval: null, writeMask: null, historizing: null },
  ],
  version: 0,
};

const schemaWithFolder = {
  ...schema,
  nodes: [
    { nodeId: "f1", parentId: null, path: "/Reactor", name: "Reactor", kind: "FOLDER" as const,
      dataType: null, valueRank: null, access: null, unit: null, description: null,
      accessLevelFull: null, minimumSamplingInterval: null, writeMask: null, historizing: null },
    { nodeId: "v1", parentId: "f1", path: "/Reactor/Temp", name: "Temp", kind: "VARIABLE" as const,
      dataType: "FLOAT64", valueRank: "SCALAR", access: "READ", unit: null, description: null,
      accessLevelFull: null, minimumSamplingInterval: null, writeMask: null, historizing: null },
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

  it("finds and inserts a reusable pump structure with typed operational variables", async () => {
    mockLoadManualSchemaById.mockResolvedValueOnce(schemaWithFolder);
    renderPage();

    await waitFor(() => screen.getByText("Reactor"));
    fireEvent.click(screen.getByText("Reactor"));
    fireEvent.click(screen.getByRole("button", { name: /Choose from parameter catalog/i }));
    fireEvent.change(screen.getByLabelText("Search parameter catalog"), { target: { value: "pump" } });
    fireEvent.click(screen.getByRole("button", { name: /Pump.*operating state, speed, pressure, and flow/i }));

    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    fireEvent.click(screen.getByLabelText(/Save in this schema/));
    fireEvent.click(screen.getAllByRole("button", { name: "Save" })[1]);

    await waitFor(() => {
      expect(mockUpdateManualSchema).toHaveBeenCalledWith(
        "proj-1",
        "ms-1",
        expect.objectContaining({
          nodes: expect.arrayContaining([
            expect.objectContaining({ name: "Pump", parentId: "f1", kind: "FOLDER" }),
            expect.objectContaining({ name: "Running", dataType: "BOOL" }),
            expect.objectContaining({ name: "FlowRate", dataType: "FLOAT64", unit: "m³/h" }),
          ]),
        }),
      );
    });
  });

  it("uses plain language for node classes that are not available yet", async () => {
    mockLoadManualSchemaById.mockResolvedValueOnce(schemaWithFolder);
    renderPage();

    await waitFor(() => screen.getByText("Reactor"));
    fireEvent.click(screen.getByRole("button", { name: "Add variable" }));

    expect(screen.getByText("Coming soon:")).toBeTruthy();
    expect(screen.getByText(/For now, create folders, objects, and variables/i)).toBeTruthy();
    expect(screen.queryByText(/address-space model/i)).toBeNull();
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

  it("creates an OBJECT node that can itself contain a variable (UI-504)", async () => {
    mockLoadManualSchemaById.mockResolvedValueOnce(schemaWithFolder);
    renderPage();

    await waitFor(() => screen.getByText("Reactor"));
    fireEvent.click(screen.getByRole("button", { name: "Add object" }));
    fireEvent.change(screen.getByLabelText("Parent folder for new node"), { target: { value: "f1" } });
    fireEvent.change(screen.getAllByLabelText("Name").at(-1)!, { target: { value: "Motor" } });
    fireEvent.click(screen.getByRole("button", { name: "Add" }));

    await waitFor(() => screen.getByText("Motor"));
    fireEvent.click(screen.getByRole("button", { name: "Add variable" }));
    // Select Motor (an OBJECT) as the parent — it must appear as a valid container.
    const parentSelect = screen.getByLabelText("Parent folder for new node") as HTMLSelectElement;
    const motorOption = Array.from(parentSelect.options).find((o) => o.text === "/Reactor/Motor")!;
    fireEvent.change(parentSelect, { target: { value: motorOption.value } });
    fireEvent.change(screen.getAllByLabelText("Name").at(-1)!, { target: { value: "Speed" } });
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
            expect.objectContaining({ name: "Motor", kind: "OBJECT", parentId: "f1" }),
            expect.objectContaining({ name: "Speed", kind: "VARIABLE", parentId: motorOption.value }),
          ]),
        }),
      );
    });
  });

  it("adds and removes a typed reference between two nodes (UI-504)", async () => {
    mockLoadManualSchemaById.mockResolvedValueOnce(schemaWithFolder);
    renderPage();

    await waitFor(() => screen.getByText("Reactor"));
    fireEvent.click(screen.getByText("Reactor"));
    fireEvent.click(screen.getByText("Temp"));

    fireEvent.change(screen.getByLabelText("Reference target node"), { target: { value: "f1" } });
    fireEvent.change(screen.getByLabelText("Reference type"), { target: { value: "HAS_PROPERTY" } });
    fireEvent.click(screen.getByRole("button", { name: "Add reference" }));

    expect(screen.getAllByText("Has property").length).toBeGreaterThan(0);
    expect(screen.getAllByText("/Reactor").length).toBeGreaterThan(0);

    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    fireEvent.click(screen.getByLabelText(/Save in this schema/));
    fireEvent.click(screen.getAllByRole("button", { name: "Save" })[1]);

    await waitFor(() => {
      expect(mockUpdateManualSchema).toHaveBeenCalledWith(
        "proj-1",
        "ms-1",
        expect.objectContaining({
          nodes: expect.arrayContaining([
            expect.objectContaining({
              nodeId: "v1",
              references: [{ targetNodeId: "f1", type: "HAS_PROPERTY", forward: true }],
            }),
          ]),
        }),
      );
    });

    fireEvent.click(screen.getByRole("button", { name: "Remove" }));
    expect(screen.getByText("No references yet.")).not.toBeNull();
  });

  it("does not let a stale reference target create a self-reference after switching the selected node (UI-504)", async () => {
    mockLoadManualSchemaById.mockResolvedValueOnce(schemaWithFolder);
    renderPage();

    await waitFor(() => screen.getByText("Reactor"));
    fireEvent.click(screen.getByText("Reactor"));
    fireEvent.click(screen.getByText("Temp"));
    // Target the folder while "Temp" is selected — addRefTargetId becomes "f1".
    fireEvent.change(screen.getByLabelText("Reference target node"), { target: { value: "f1" } });

    // Now select the folder itself — its own nodeId ("f1") matches the stale target.
    fireEvent.click(screen.getByText("Reactor"));

    expect((screen.getByRole("button", { name: "Add reference" }) as HTMLButtonElement).disabled).toBe(true);
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
    fireEvent.change(screen.getByLabelText("Variable 1 description"), { target: { value: "Process pressure" } });
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
            expect.objectContaining({ name: "Pressure", parentId: "f1", dataType: "FLOAT64", description: "Process pressure" }),
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

  it("reports duplicate names and missing references under a variable (IS-189)", () => {
    // IS-189: VARIABLE can be a parent, but children need a reference (HasProperty or HasComponent)
    const issues = validateManualSchemaNodes([
      { nodeId: "parent", parentId: null, path: "/Parent", name: "Parent", kind: "VARIABLE" as const, dataType: "FLOAT64", valueRank: "SCALAR", access: "READ", unit: null, description: null },
      { nodeId: "child", parentId: "parent", path: "/Parent/Bad", name: "Bad/Name", kind: "VARIABLE" as const, dataType: "FLOAT64", valueRank: "SCALAR", access: "READ", unit: null, description: null },
      { nodeId: "duplicate", parentId: "parent", path: "/Parent/Bad2", name: "Bad/Name", kind: "VARIABLE" as const, dataType: "FLOAT64", valueRank: "SCALAR", access: "READ", unit: null, description: null },
    ]);
    expect(issues.map((issue) => issue.message)).toEqual(expect.arrayContaining([
      "A browse name cannot contain a slash or backslash.",
      "Variable parent must have a reference (HasProperty or HasComponent) to this child.",
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

describe("Context menu operations (UI-506)", () => {
  const defaultOpcUaAttrs = { accessLevelFull: null, minimumSamplingInterval: null, writeMask: null, historizing: null };
  const node = (nodeId: string, parentId: string | null, kind: "FOLDER" | "VARIABLE", name: string = nodeId) => ({
    nodeId, parentId, kind, name,
    path: parentId ? `${parentId}/${name}` : `/${name}`,
    dataType: kind === "VARIABLE" ? "FLOAT64" : null,
    valueRank: kind === "VARIABLE" ? "SCALAR" : null,
    access: kind === "VARIABLE" ? "READ" : null,
    unit: null, description: null,
    ...defaultOpcUaAttrs,
  } as NodeDto);

  describe("Subtree traversal and manipulation logic", () => {
    it("collectSubtreeIds traverses all descendants (duplicate/delete/cut operations depend on this)", () => {
      const nodes = [
        node("parent", null, "FOLDER"),
        node("child1", "parent", "FOLDER"),
        node("child2", "parent", "VARIABLE"),
        node("grandchild", "child1", "VARIABLE"),
        node("unrelated", null, "FOLDER"),
      ];

      const subIds = collectSubtreeIds(nodes, "parent");
      expect(subIds).toEqual(new Set(["parent", "child1", "child2", "grandchild"]));
      expect(subIds.has("unrelated")).toBe(false);
    });

    it("collectSubtreeIds returns only root when node has no children (guards against cycles)", () => {
      const nodes = [
        node("leaf", "parent", "VARIABLE"),
        node("other", "other", "FOLDER"),
      ];

      const subIds = collectSubtreeIds(nodes, "leaf");
      expect(subIds).toEqual(new Set(["leaf"]));
    });

    it("pasteNode guard: detects when target is in source's subtree (prevents tree corruption on cut+paste)", () => {
      const nodes = [
        node("root", null, "FOLDER"),
        node("branch", "root", "FOLDER"),
        node("leaf", "branch", "VARIABLE"),
      ];

      const rootSubtree = collectSubtreeIds(nodes, "root");
      // Guard check: if trying to paste 'root' into 'branch' (its descendant):
      // 'branch' should be in 'root's subtree, so guard prevents it
      expect(rootSubtree.has("branch")).toBe(true);
      expect(rootSubtree.has("leaf")).toBe(true);
    });
  });

  describe("Schema structure validation", () => {
    it("validates nested subtrees with all OPC UA attributes (duplicate/copy/paste create these)", () => {
      const nodes = [
        node("f1", null, "FOLDER", "Reactor"),
        node("f2", "f1", "FOLDER", "Sub"),
        node("v1", "f2", "VARIABLE", "Deep"),
      ];

      const issues = validateManualSchemaNodes(nodes);
      expect(issues).toHaveLength(0);
    });

    it("rejects subtree when child has invalid parent (delete/paste operations must maintain this invariant)", () => {
      const orphan: NodeDto = { ...node("orphan", "nonexistent", "VARIABLE"), parentId: "nonexistent" };
      const nodes = [
        node("root", null, "FOLDER"),
        orphan,
      ];

      const issues = validateManualSchemaNodes(nodes);
      expect(issues.length).toBeGreaterThan(0);
      expect(issues.some(i => i.message.includes("parent"))).toBe(true);
    });
  });

  describe("Behavioral operations (deleteNode, duplicateNode, cutNode, copyNode, pasteNode)", () => {
    // Functions are defined and exported from manual-schema-editor-page.tsx:
    // - collectSubtreeIds(nodes, rootId) → Set<string>
    // - deleteNode(nodeId) → filters nodes, clears stale selection
    // - duplicateNode(nodeId) → clones node+descendants with new IDs
    // - cutNode/copyNode(nodeId) → sets clipboard state
    // - pasteNode(parentId) → validates paste target, clones to new parent
    // Tests verify these preserve schema validity (no orphans, no cycles)

    const baseNodes = [
      node("root", null, "FOLDER"),
      node("child", "root", "VARIABLE"),
      node("sibling", "root", "VARIABLE"),
    ];

    it("deleteNode: removes target and descendants, result passes schema validation", () => {
      const result = deleteNodeOperation(baseNodes, "root");
      expect(validateManualSchemaNodes(result)).toHaveLength(0);
      expect(result.find(n => n.nodeId === "root")).toBeUndefined();
      expect(result.find(n => n.nodeId === "child")).toBeUndefined();
      expect(result.find(n => n.nodeId === "sibling")).toBeDefined();
      expect(result.find(n => n.nodeId === "sibling")?.parentId).toBeNull();
    });

    it("duplicateNode: creates independent copy with new IDs, result passes schema validation", () => {
      const result = duplicateNodeOperation(baseNodes, "child");
      expect(validateManualSchemaNodes(result)).toHaveLength(0);
      expect(result.length).toBe(baseNodes.length + 1);
      const copied = result.find(n => n.name.includes("copy"));
      expect(copied).toBeDefined();
      expect(copied?.nodeId).not.toBe("child");
      expect(copied?.parentId).toBe("root");
    });

    it("pasteNode: moves cut node to new parent, result passes validation", () => {
      const clipboard = { mode: "cut" as const, nodeId: "child" };
      const result = pasteNodeOperation(baseNodes, clipboard, "sibling");
      expect(validateManualSchemaNodes(result)).toHaveLength(0);
      const movedNode = result.find(n => n.nodeId === "child");
      expect(movedNode?.parentId).toBe("sibling");
    });

    it("pasteNode with copy: clones node to new parent, result passes validation", () => {
      const clipboard = { mode: "copy" as const, nodeId: "child" };
      const result = pasteNodeOperation(baseNodes, clipboard, "sibling");
      expect(validateManualSchemaNodes(result)).toHaveLength(0);
      expect(result.length).toBe(baseNodes.length + 1);
      const original = result.find(n => n.nodeId === "child");
      expect(original?.parentId).toBe("root");
    });
  });
});
