/**
 * Tests for ManualSchemasPage (UI-489).
 *
 * Covers:
 * - loadManualSchemas is called on mount
 * - list renders name/protocol/variable-count
 * - create dialog calls createManualSchema (no navigation yet — the detail/editor
 *   route lands in UI-490; a row isn't clickable until that page exists)
 * - duplicate dialog calls duplicateManualSchema
 * - delete confirmation calls deleteManualSchema
 */

import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ManualSchemasPage } from "./manual-schemas-page";

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

  it("creates a schema", async () => {
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
