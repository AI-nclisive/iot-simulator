/**
 * Tests for ManualSchemasPage (UI-489/UI-490).
 *
 * Covers:
 * - loadManualSchemas is called on mount
 * - list renders name/protocol/variable-count
 * - create dialog calls createManualSchema and navigates to the new schema's editor
 * - a row click navigates to that schema's editor
 * - duplicate dialog calls duplicateManualSchema
 * - delete confirmation calls deleteManualSchema
 */

import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ManualSchemasPage } from "./manual-schemas-page";

const { mockNavigate } = vi.hoisted(() => ({ mockNavigate: vi.fn() }));

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return { ...actual, useNavigate: () => mockNavigate };
});

const {
  mockLoadManualSchemas,
  mockCreateManualSchema,
  mockDuplicateManualSchema,
  mockDeleteManualSchema,
} = vi.hoisted(() => ({
  mockLoadManualSchemas: vi.fn().mockResolvedValue(undefined),
  mockCreateManualSchema: vi.fn(),
  mockDuplicateManualSchema: vi.fn(),
  mockDeleteManualSchema: vi.fn().mockResolvedValue(undefined),
}));

const schema = {
  id: "ms-1",
  projectId: "proj-1",
  protocol: "OPC_UA",
  name: "Boiler layout",
  description: null,
  nodes: [
    { nodeId: "v1", parentId: null, path: "/v1", name: "v1", kind: "VARIABLE" as const,
      dataType: "FLOAT64", valueRank: "SCALAR", access: "READ", unit: null, description: null },
  ],
  version: 0,
};

vi.mock("../shell/shell-store", () => ({
  useShellStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ accessMode: "local", sharedRole: "admin", currentProjectId: "proj-1" }),
}));

vi.mock("../shell/manual-schemas-store", () => ({
  useManualSchemasStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({
      schemas: [schema],
      isLoading: false,
      error: null,
      loadManualSchemas: mockLoadManualSchemas,
      createManualSchema: mockCreateManualSchema,
      duplicateManualSchema: mockDuplicateManualSchema,
      deleteManualSchema: mockDeleteManualSchema,
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
      <ManualSchemasPage />
    </MemoryRouter>,
  );
}

describe("ManualSchemasPage (UI-489)", () => {
  it("loads manual schemas on mount and renders the list", () => {
    renderPage();
    expect(mockLoadManualSchemas).toHaveBeenCalledWith("proj-1");
    expect(screen.getByText("Boiler layout")).not.toBeNull();
    expect(screen.getByText("OPC UA")).not.toBeNull();
  });

  it("creates a schema and navigates to its editor", async () => {
    mockCreateManualSchema.mockResolvedValueOnce({ ...schema, id: "ms-new" });
    renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Create manual schema" }));
    fireEvent.change(screen.getByPlaceholderText("Boiler layout"), {
      target: { value: "New schema" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Create" }));

    await waitFor(() => {
      expect(mockCreateManualSchema).toHaveBeenCalledWith(
        "proj-1",
        expect.objectContaining({ protocol: "OPC_UA", name: "New schema" }),
      );
    });
    expect(mockNavigate).toHaveBeenCalledWith("/manual-schemas/ms-new");
  });

  it("navigates to the editor when a row is clicked", () => {
    renderPage();
    fireEvent.click(screen.getByText("Boiler layout"));
    expect(mockNavigate).toHaveBeenCalledWith("/manual-schemas/ms-1");
  });

  it("duplicates a schema with the entered name", async () => {
    mockDuplicateManualSchema.mockResolvedValueOnce({ ...schema, id: "ms-copy" });
    renderPage();

    fireEvent.click(screen.getByText("Duplicate"));
    const input = screen.getByDisplayValue("Boiler layout (copy)");
    fireEvent.change(input, { target: { value: "Boiler layout v2" } });
    fireEvent.click(screen.getAllByRole("button", { name: "Duplicate" }).at(-1)!);

    await waitFor(() => {
      expect(mockDuplicateManualSchema).toHaveBeenCalledWith("proj-1", "ms-1", "Boiler layout v2");
    });
  });

  it("deletes a schema after confirmation", async () => {
    renderPage();

    fireEvent.click(screen.getByLabelText("Delete manual schema Boiler layout"));
    fireEvent.click(screen.getByRole("button", { name: "Delete manual schema" }));

    await waitFor(() => {
      expect(mockDeleteManualSchema).toHaveBeenCalledWith("proj-1", "ms-1");
    });
  });
});
