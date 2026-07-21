import { describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
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
    parameterCount: 1,
    status: "Stopped",
    health: "Healthy",
  },
];

// UI-483: the Pattern select defaults to 3 plain-language options, with a
// "Show more patterns" control revealing the remaining (advanced/jargon) 3.
describe("SyntheticProfileStep — Pattern select progressive disclosure (UI-483)", () => {
  async function renderWithOneMeasurement() {
    mockApiFetch.mockReset();
    mockApiFetch.mockImplementation(async (path: string) => {
      if (path.includes("/recordings")) return { items: [] };
      if (path.endsWith("/schema")) {
        return {
          id: "schema1",
          dataSourceId: "s1",
          version: 1,
          nodes: [
            {
              nodeId: "n1",
              path: "n1",
              name: "Temperature",
              kind: "VARIABLE",
              dataType: "FLOAT64",
              unit: null,
            },
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
    await waitFor(() => expect(patternSelect()).not.toBeNull());
    return user;
  }

  /** The Pattern <select> is the only combobox whose options include "Fixed value". */
  function patternSelect(): HTMLSelectElement | null {
    return (
      screen
        .queryAllByRole("combobox")
        .find((el) => within(el).queryByText("Fixed value") != null) as HTMLSelectElement | undefined
    ) ?? null;
  }

  it("shows only the 3 plain-language options by default", async () => {
    await renderWithOneMeasurement();
    const select = patternSelect()!;
    const options = within(select).getAllByRole("option").map((o) => o.textContent);
    expect(options).toEqual(["Fixed value", "Smooth (rises & falls)", "Random"]);
  });

  it("reveals the advanced options after clicking Show more patterns", async () => {
    const user = await renderWithOneMeasurement();
    await user.click(screen.getByRole("button", { name: /Show more patterns/i }));
    const select = patternSelect()!;
    const options = within(select).getAllByRole("option").map((o) => o.textContent);
    expect(options).toEqual([
      "Fixed value",
      "Smooth (rises & falls)",
      "Random",
      "Rising ramp (resets)",
      "Alternating (on/off)",
      "Random (drifting)",
    ]);
    expect(screen.queryByRole("button", { name: /Show more patterns/i })).toBeNull();
  });

  it("stays expanded once an advanced pattern is selected", async () => {
    const user = await renderWithOneMeasurement();
    await user.click(screen.getByRole("button", { name: /Show more patterns/i }));
    const select = patternSelect()!;
    await user.selectOptions(select, "Rising ramp (resets)");
    // Still expanded (6 options, no "Show more" button) after re-render.
    const optionsAfter = within(patternSelect()!).getAllByRole("option").map((o) => o.textContent);
    expect(optionsAfter).toHaveLength(6);
    expect(screen.queryByRole("button", { name: /Show more patterns/i })).toBeNull();
  });

  it("auto-expands a row whose pattern was already advanced without the user ever clicking Show more", async () => {
    // A pattern can arrive already-advanced without any interactive pattern selection —
    // e.g. "Prefill from recording" (IS-146) applying a RAMP-recommended suggestion. The
    // row must render expanded immediately, not require the user to discover "Show more".
    mockApiFetch.mockReset();
    mockApiFetch.mockImplementation(async (path: string) => {
      if (path.includes("/derive-synthetic")) {
        return {
          measurements: [
            {
              nodeId: "n1",
              dataType: "FLOAT64",
              updateRateMs: 1000,
              recommended: "RAMP",
              suggestions: { RAMP: { type: "RAMP", min: 0, max: 10, periodMs: 5000 } },
            },
          ],
        };
      }
      if (path.includes("/recordings")) return { items: [{ id: "rec1", name: null, valueCount: 5 }] };
      if (path.endsWith("/schema")) {
        return {
          id: "schema1",
          dataSourceId: "s1",
          version: 1,
          nodes: [
            { nodeId: "n1", path: "n1", name: "Temperature", kind: "VARIABLE", dataType: "FLOAT64", unit: null },
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
    await waitFor(() => expect(patternSelect()).not.toBeNull());

    const recordingCombo = screen
      .getAllByRole("combobox")
      .find((el) => within(el).queryByText(/5 values/) != null)!;
    await user.selectOptions(recordingCombo, "rec1");
    await user.click(screen.getByRole("button", { name: /^Prefill$/ }));

    await waitFor(() => expect(patternSelect()!.value).toBe("RAMP"));
    const options = within(patternSelect()!).getAllByRole("option").map((o) => o.textContent);
    expect(options).toHaveLength(6);
    expect(screen.queryByRole("button", { name: /Show more patterns/i })).toBeNull();
  });
});

// UI-486: the pattern editor's numeric fields must accept decimal values — the backend
// already stores min/max/period/volatility/updateRate as Double, so nothing but a missing
// `step` attribute (defaulting the browser to integer-only step semantics) was blocking it.
describe("SyntheticProfileStep — decimal number fields (UI-486)", () => {
  async function renderWithOneMeasurement() {
    mockApiFetch.mockReset();
    mockApiFetch.mockImplementation(async (path: string) => {
      if (path.includes("/recordings")) return { items: [] };
      if (path.endsWith("/schema")) {
        return {
          id: "schema1",
          dataSourceId: "s1",
          version: 1,
          nodes: [
            { nodeId: "n1", path: "n1", name: "Temperature", kind: "VARIABLE", dataType: "FLOAT64", unit: null },
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
    await waitFor(() => expect(screen.queryByRole("list")).not.toBeNull());
    return user;
  }

  function numberInputs(): HTMLInputElement[] {
    return [...document.querySelectorAll('input[type="number"]')] as HTMLInputElement[];
  }

  it("every numeric pattern field allows decimals (step=any)", async () => {
    await renderWithOneMeasurement();
    const inputs = numberInputs();
    expect(inputs.length).toBeGreaterThan(0);
    expect(inputs.every((el) => el.getAttribute("step") === "any")).toBe(true);
  });

  it("accepts a decimal value typed into Min and reports it as valid", async () => {
    const user = await renderWithOneMeasurement();
    const minInput = screen.getByLabelText(/^Min$/i) as HTMLInputElement;
    await user.clear(minInput);
    await user.type(minInput, "36.6");
    expect(minInput.value).toBe("36.6");
    expect(minInput.validity.valid).toBe(true);
  });
});
