/**
 * Tests for useManualSchemasStore (UI-489).
 *
 * Mocks apiFetch. Covers load/create/duplicate/delete.
 */

import { afterEach, beforeEach, describe, expect, it, vi, type MockedFunction } from "vitest";
import { apiFetch, ApiError } from "../api";
import { useManualSchemasStore } from "./manual-schemas-store";

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
}));

const mockApiFetch = apiFetch as MockedFunction<typeof apiFetch>;

function makeSchema(overrides: Partial<{ id: string; name: string; version: number }> = {}) {
  return {
    id: overrides.id ?? "ms-01",
    projectId: "proj-1",
    protocol: "OPC_UA",
    name: overrides.name ?? "Boiler",
    description: null,
    nodes: [],
    version: overrides.version ?? 0,
  };
}

beforeEach(() => {
  useManualSchemasStore.setState({ schemas: [], isLoading: false, error: null });
});

afterEach(() => {
  vi.clearAllMocks();
});

describe("loadManualSchemas", () => {
  it("populates schemas from the Page envelope", async () => {
    mockApiFetch.mockResolvedValueOnce({ items: [makeSchema()], nextCursor: null, limit: 50 });

    await useManualSchemasStore.getState().loadManualSchemas("proj-1");

    expect(mockApiFetch).toHaveBeenCalledWith("/api/v1/projects/proj-1/manual-schemas");
    expect(useManualSchemasStore.getState().schemas).toHaveLength(1);
    expect(useManualSchemasStore.getState().isLoading).toBe(false);
  });

  it("sets error state on failure", async () => {
    mockApiFetch.mockRejectedValueOnce(new ApiError(500, "Server error", undefined, undefined));

    await useManualSchemasStore.getState().loadManualSchemas("proj-1");

    expect(useManualSchemasStore.getState().error).toBe("Server error");
    expect(useManualSchemasStore.getState().isLoading).toBe(false);
  });
});

describe("createManualSchema", () => {
  it("POSTs and prepends the new schema", async () => {
    mockApiFetch.mockResolvedValueOnce(makeSchema({ id: "ms-new", name: "New one" }));

    const created = await useManualSchemasStore
      .getState()
      .createManualSchema("proj-1", { protocol: "OPC_UA", name: "New one" });

    expect(mockApiFetch).toHaveBeenCalledWith(
      "/api/v1/projects/proj-1/manual-schemas",
      expect.objectContaining({ method: "POST" }),
    );
    expect(created.id).toBe("ms-new");
    expect(useManualSchemasStore.getState().schemas[0].id).toBe("ms-new");
  });
});

describe("duplicateManualSchema", () => {
  it("POSTs to /duplicate and prepends the copy", async () => {
    mockApiFetch.mockResolvedValueOnce(makeSchema({ id: "ms-copy", name: "Boiler (copy)" }));

    const copy = await useManualSchemasStore
      .getState()
      .duplicateManualSchema("proj-1", "ms-01", "Boiler (copy)");

    expect(mockApiFetch).toHaveBeenCalledWith(
      "/api/v1/projects/proj-1/manual-schemas/ms-01/duplicate",
      expect.objectContaining({ method: "POST" }),
    );
    expect(copy.id).toBe("ms-copy");
    expect(useManualSchemasStore.getState().schemas[0].id).toBe("ms-copy");
  });
});

describe("deleteManualSchema", () => {
  it("DELETEs and removes the schema from the list", async () => {
    useManualSchemasStore.setState({ schemas: [makeSchema()] });
    mockApiFetch.mockResolvedValueOnce(undefined);

    await useManualSchemasStore.getState().deleteManualSchema("proj-1", "ms-01");

    expect(mockApiFetch).toHaveBeenCalledWith(
      "/api/v1/projects/proj-1/manual-schemas/ms-01",
      expect.objectContaining({ method: "DELETE" }),
    );
    expect(useManualSchemasStore.getState().schemas).toHaveLength(0);
  });
});
