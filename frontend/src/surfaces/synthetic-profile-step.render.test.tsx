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

  /**
   * The per-row Pattern <select> is the combobox whose options include "Fixed value" —
   * scoped to the measurement list so the bulk "Set pattern for selected…" control
   * (which lists the same pattern options) isn't picked up instead.
   */
  function patternSelect(): HTMLSelectElement | null {
    const list = screen.queryByRole("list");
    if (!list) return null;
    return (
      within(list)
        .queryAllByRole("combobox")
        .find((el) => within(el).queryByText("Fixed value") != null) as HTMLSelectElement | undefined
    ) ?? null;
  }

  it("shows only the 3 plain-language options by default", async () => {
    await renderWithOneMeasurement();
    const select = patternSelect()!;
    const options = within(select).getAllByRole("option").map((o) => o.textContent);
    expect(options).toEqual(["Fixed value", "Wave (rises and falls repeatedly)", "Random"]);
  });

  it("reveals the advanced options after clicking Show more patterns", async () => {
    const user = await renderWithOneMeasurement();
    await user.click(screen.getByRole("button", { name: /Show more patterns/i }));
    const select = patternSelect()!;
    const options = within(select).getAllByRole("option").map((o) => o.textContent);
    expect(options).toEqual([
      "Fixed value",
      "Wave (rises and falls repeatedly)",
      "Random",
      "Rising ramp (returns to start)",
      "Alternating (low/high)",
      "Random drift",
    ]);
    expect(screen.queryByRole("button", { name: /Show more patterns/i })).toBeNull();
  });

  it("stays expanded once an advanced pattern is selected", async () => {
    const user = await renderWithOneMeasurement();
    await user.click(screen.getByRole("button", { name: /Show more patterns/i }));
    const select = patternSelect()!;
    await user.selectOptions(select, "Rising ramp (returns to start)");
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

  it("float range fields allow decimals while timing fields require whole milliseconds", async () => {
    await renderWithOneMeasurement();
    const inputs = numberInputs();
    expect(inputs.length).toBeGreaterThan(0);
    expect((screen.getByLabelText(/^Min$/i) as HTMLInputElement).getAttribute("step")).toBe("any");
    expect((screen.getByLabelText(/^Max$/i) as HTMLInputElement).getAttribute("step")).toBe("any");
    expect((screen.getByLabelText(/^Update rate \(ms\)$/i) as HTMLInputElement).getAttribute("step")).toBe("1");
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

describe("SyntheticProfileStep — select-all / deselect-all (UI-487)", () => {
  async function renderWithTwoMeasurements() {
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
            { nodeId: "n2", path: "n2", name: "Pressure", kind: "VARIABLE", dataType: "FLOAT64", unit: null },
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
    await waitFor(() => expect(rowCheckboxes()).toHaveLength(2));
    return user;
  }

  function rowCheckboxes(): HTMLInputElement[] {
    const list = screen.getByRole("list");
    return within(list).getAllByRole("checkbox") as HTMLInputElement[];
  }

  function selectAllCheckbox(): HTMLInputElement {
    return screen.getByRole("checkbox", { name: /Select all/i }) as HTMLInputElement;
  }

  it("both rows start enabled, so Select all starts checked", async () => {
    await renderWithTwoMeasurements();
    expect(rowCheckboxes().every((c) => c.checked)).toBe(true);
    expect(selectAllCheckbox().checked).toBe(true);
  });

  it("Select all deselects every row, and re-checking selects every row again", async () => {
    const user = await renderWithTwoMeasurements();
    await user.click(selectAllCheckbox());
    expect(rowCheckboxes().every((c) => !c.checked)).toBe(true);

    await user.click(selectAllCheckbox());
    expect(rowCheckboxes().every((c) => c.checked)).toBe(true);
  });

  it("unchecking one row leaves Select all indeterminate, not checked", async () => {
    const user = await renderWithTwoMeasurements();
    await user.click(rowCheckboxes()[0]);
    expect(selectAllCheckbox().checked).toBe(false);
    expect(selectAllCheckbox().indeterminate).toBe(true);
  });

  function bulkPatternSelect(): HTMLSelectElement {
    return screen.getByRole("combobox", { name: /Set pattern for selected/i }) as HTMLSelectElement;
  }

  function rowPatternSelects(): HTMLSelectElement[] {
    const list = screen.getByRole("list");
    return within(list)
      .getAllByRole("combobox")
      .filter((el) => within(el).queryByText("Fixed value") != null) as HTMLSelectElement[];
  }

  it("keeps advanced bulk patterns out of the default dropdown", async () => {
    const user = await renderWithTwoMeasurements();
    const options = within(bulkPatternSelect()).getAllByRole("option").map((option) => option.textContent);
    expect(options).toEqual(["Set pattern for selected…", "Fixed value", "Wave (rises and falls repeatedly)", "Random"]);

    await user.click(screen.getByRole("button", { name: "More patterns…" }));
    expect(within(bulkPatternSelect()).getAllByRole("option").map((option) => option.textContent)).toContain("Random drift");
  });

  it("keeps repeatable-results settings out of the main form until requested", async () => {
    const user = await renderWithTwoMeasurements();
    expect(screen.queryByRole("spinbutton", { name: /Repeatable results/i })).toBeNull();

    await user.click(screen.getByRole("button", { name: "Advanced generation settings" }));
    expect(screen.getByRole("spinbutton", { name: /Repeatable results/i })).not.toBeNull();
  });

  it("bulk pattern select applies the chosen pattern to every currently-selected row (UI-488)", async () => {
    const user = await renderWithTwoMeasurements();
    await user.selectOptions(bulkPatternSelect(), "Random");
    expect(rowPatternSelects().every((s) => s.value === "RANDOM_UNIFORM")).toBe(true);
  });

  it("bulk pattern select skips rows that were deselected first (UI-488)", async () => {
    const user = await renderWithTwoMeasurements();
    // Deselect the second row — collapses its detail section (including its Pattern select).
    await user.click(rowCheckboxes()[1]);
    expect(rowPatternSelects()).toHaveLength(1);

    await user.selectOptions(bulkPatternSelect(), "Wave (rises and falls repeatedly)");
    expect(rowPatternSelects()[0].value).toBe("SINE");

    // Re-select the second row and confirm its pattern was left at its untouched default
    // (not overwritten by the bulk apply while it was deselected).
    await user.click(rowCheckboxes()[1]);
    expect(rowPatternSelects()[1].value).toBe("RANDOM_UNIFORM");
  });

  function bulkRateInput(): HTMLInputElement {
    return screen.getByLabelText(/Update rate for selected/i) as HTMLInputElement;
  }

  function rowUpdateRateInputs(): HTMLInputElement[] {
    const list = screen.getByRole("list");
    return within(list).getAllByLabelText(/^Update rate \(ms\)$/i) as HTMLInputElement[];
  }

  it("bulk update-rate applies one rate to every currently-selected row", async () => {
    const user = await renderWithTwoMeasurements();
    await user.type(bulkRateInput(), "60000");
    await user.click(screen.getByRole("button", { name: "Apply rate" }));
    expect(rowUpdateRateInputs().every((i) => i.value === "60000")).toBe(true);
  });

  it("bulk update-rate skips rows that were deselected first", async () => {
    const user = await renderWithTwoMeasurements();
    await user.click(rowCheckboxes()[1]);
    await user.type(bulkRateInput(), "60000");
    await user.click(screen.getByRole("button", { name: "Apply rate" }));
    expect(rowUpdateRateInputs()[0].value).toBe("60000");

    await user.click(rowCheckboxes()[1]);
    expect(rowUpdateRateInputs()[1].value).toBe("1000");
  });
});

// UI-499: a BOOL node's "Alternating true/false" / "Random true/false" patterns must not
// show the generic Add/Remove values-list editor — a boolean only ever has two possible
// values, so there is nothing for the user to curate.
describe("SyntheticProfileStep — BOOL pattern editor (UI-499)", () => {
  async function renderWithBoolMeasurement() {
    mockApiFetch.mockReset();
    mockApiFetch.mockImplementation(async (path: string) => {
      if (path.includes("/recordings")) return { items: [] };
      if (path.endsWith("/schema")) {
        return {
          id: "schema1",
          dataSourceId: "s1",
          version: 1,
          nodes: [
            { nodeId: "n1", path: "n1", name: "Running", kind: "VARIABLE", dataType: "BOOL", unit: null },
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
    await waitFor(() => expect(screen.queryByText("Running")).not.toBeNull());
    const list = screen.getByRole("list");
    const select = within(list)
      .getAllByRole("combobox")
      .find((el) => within(el).queryByText("Fixed true/false") != null) as HTMLSelectElement;
    return { user, select };
  }

  it("shows a description instead of Add/Remove controls for Alternating true/false", async () => {
    const { user, select } = await renderWithBoolMeasurement();
    await user.selectOptions(select, "Alternating true/false");

    expect(screen.getByText("Alternates between True and False on every update.")).not.toBeNull();
    expect(screen.queryByRole("button", { name: "Add value" })).toBeNull();
    expect(screen.queryByRole("button", { name: /^Remove value/ })).toBeNull();
  });

  it("shows a description instead of Add/Remove controls for Random true/false", async () => {
    const { user, select } = await renderWithBoolMeasurement();
    await user.selectOptions(select, "Random true/false");

    expect(screen.getByText("Picks True or False at random on every update.")).not.toBeNull();
    expect(screen.queryByRole("button", { name: "Add value" })).toBeNull();
    expect(screen.queryByRole("button", { name: /^Remove value/ })).toBeNull();
  });

  it("still keeps the fixed True/False select for the Fixed true/false (CONSTANT) pattern", async () => {
    const { select } = await renderWithBoolMeasurement();
    expect(select.value).toBe("CONSTANT");
    expect(screen.queryByRole("button", { name: "Add value" })).toBeNull();
  });
});
