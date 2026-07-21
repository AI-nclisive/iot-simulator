/**
 * Tests for SyntheticProfileStep — Manual Schema parameter source (UI-491).
 *
 * Covers:
 * - picking a manual schema populates the measurement list from its nodes
 * - onChange emits manualSchemaId (and schemaFromSourceId: null)
 * - switching back to "Existing source" clears the manual schema selection
 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SyntheticProfileStep, type SyntheticProfileValue } from "./synthetic-profile-step";
import { useManualSchemasStore } from "../shell/manual-schemas-store";
import type { DataSourceRow } from "../shell/data-sources-store";

const { mockApiFetch } = vi.hoisted(() => ({ mockApiFetch: vi.fn() }));
vi.mock("../api", async () => {
  const actual = await vi.importActual<typeof import("../api")>("../api");
  return { ...actual, apiFetch: mockApiFetch };
});
vi.mock("../shell/notification-store", () => ({
  useNotificationStore: (selector: (s: { push: () => void }) => unknown) => selector({ push: () => {} }),
}));

const sources: DataSourceRow[] = [
  { id: "s1", name: "Source 1", protocol: "OPC UA", endpoint: "opc.tcp://localhost:4840",
    parameterCount: 1, status: "Stopped", health: "Healthy" },
];

const manualSchema = {
  id: "ms1",
  projectId: "p1",
  protocol: "OPC_UA",
  name: "Boiler layout",
  description: null,
  nodes: [
    { nodeId: "v1", parentId: null, path: "/v1", name: "Level", kind: "VARIABLE" as const,
      dataType: "FLOAT64", valueRank: "SCALAR", access: "READ", unit: null, description: null },
  ],
  version: 0,
};

beforeEach(() => {
  useManualSchemasStore.setState({ schemas: [manualSchema], isLoading: false, error: null });
  mockApiFetch.mockReset();
  mockApiFetch.mockImplementation(async (path: string) => {
    if (path.includes("/recordings")) return { items: [] };
    throw new Error(`unexpected apiFetch: ${path}`);
  });
});

afterEach(() => {
  cleanup();
  useManualSchemasStore.setState({ schemas: [], isLoading: false, error: null });
});

function renderStep(onChange: (v: SyntheticProfileValue) => void) {
  return render(
    <SyntheticProfileStep projectId="p1" sources={sources} seed="" onSeedChange={() => {}} onChange={onChange} />,
  );
}

describe("SyntheticProfileStep — Manual Schema parameter source (UI-491)", () => {
  it("picking a manual schema populates measurements and emits manualSchemaId", async () => {
    const onChange = vi.fn();
    renderStep(onChange);
    const user = userEvent.setup();

    await user.click(screen.getByRole("radio", { name: "Manual schema" }));
    await user.selectOptions(screen.getByLabelText(/Reuse a manual schema/i), "ms1");

    await waitFor(() => {
      expect(screen.getByText("Level")).not.toBeNull();
    });
    await waitFor(() => {
      expect(onChange).toHaveBeenCalledWith(
        expect.objectContaining({ manualSchemaId: "ms1", schemaFromSourceId: null }),
      );
    });
  });

  it("switching back to existing source clears the manual schema selection", async () => {
    const onChange = vi.fn();
    renderStep(onChange);
    const user = userEvent.setup();

    await user.click(screen.getByRole("radio", { name: "Manual schema" }));
    await user.selectOptions(screen.getByLabelText(/Reuse a manual schema/i), "ms1");
    await waitFor(() => screen.getByText("Level"));

    await user.click(screen.getByRole("radio", { name: "Existing source" }));

    await waitFor(() => {
      expect(onChange).toHaveBeenCalledWith(
        expect.objectContaining({ manualSchemaId: null, schemaFromSourceId: null }),
      );
    });
  });
});
