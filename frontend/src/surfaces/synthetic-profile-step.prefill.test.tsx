import { describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SyntheticProfileStep } from "./synthetic-profile-step";
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
  {
    id: "s1",
    name: "Source 1",
    protocol: "OPC UA",
    endpoint: "opc.tcp://localhost:4840",
    parameterCount: 2,
    status: "Stopped",
    health: "Healthy",
  },
];

// UI-484: prefill only ever touches the subset of measurements the recording actually
// covers — the affected rows must be visually identifiable (badge) among the rest.
describe("SyntheticProfileStep — Prefill marks only the measurements it actually changed (UI-484)", () => {
  it("badges only the matched measurement after a successful prefill", async () => {
    mockApiFetch.mockReset();
    mockApiFetch.mockImplementation(async (path: string) => {
      if (path.includes("/recordings/rec1/derive-synthetic")) {
        return {
          measurements: [
            {
              nodeId: "n1",
              dataType: "FLOAT64",
              updateRateMs: 1000,
              recommended: "RANDOM_WALK",
              suggestions: {
                RANDOM_WALK: { type: "RANDOM_WALK", min: 75, max: 100, volatility: 0.25 },
              },
            },
          ],
        };
      }
      if (path.includes("/recordings")) {
        return { items: [{ id: "rec1", name: null, valueCount: 12 }] };
      }
      if (path.endsWith("/schema")) {
        return {
          id: "schema1",
          dataSourceId: "s1",
          version: 1,
          nodes: [
            { nodeId: "n1", path: "n1", name: "Matched", kind: "VARIABLE", dataType: "FLOAT64", unit: null },
            { nodeId: "n2", path: "n2", name: "Unmatched", kind: "VARIABLE", dataType: "FLOAT64", unit: null },
          ],
        };
      }
      throw new Error(`unexpected apiFetch: ${path}`);
    });

    render(
      <SyntheticProfileStep
        projectId="p1"
        sources={sources}
        seed=""
        onSeedChange={() => {}}
        onChange={() => {}}
      />,
    );

    const user = userEvent.setup();
    await user.selectOptions(screen.getByLabelText(/Reuse schema from source/i), "s1");
    await waitFor(() => expect(screen.getByText("Matched")).toBeTruthy());

    // No badges before prefill.
    expect(screen.queryByText("Prefilled")).toBeNull();

    // Select the only recording option (label contains its id since name is null).
    const selects = screen.getAllByRole("combobox");
    const recordingCombo = selects.find((el) =>
      Array.from(el.querySelectorAll("option")).some((o) => o.textContent?.includes("12 values")),
    )!;
    await user.selectOptions(recordingCombo, "rec1");
    await user.click(screen.getByRole("button", { name: /^Prefill$/ }));

    await waitFor(() => expect(screen.getAllByText("Prefilled")).toHaveLength(1));
    // Exactly the matched row is badged; the unmatched one is not.
    const matchedRow = screen.getByText("Matched").closest("li")!;
    const unmatchedRow = screen.getByText("Unmatched").closest("li")!;
    expect(matchedRow.textContent).toContain("Prefilled");
    expect(unmatchedRow.textContent).not.toContain("Prefilled");
  });
});
