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
import { ManualSchemaEditorPage } from "./manual-schema-editor-page";

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
});
