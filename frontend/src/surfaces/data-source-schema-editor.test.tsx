/**
 * Tests for DataSourceSchemaEditor dependency warnings (UI-043)
 *
 * Covers:
 * - Unit change on a referenced param (hasDependent=true) → unit warning appears
 * - Range narrowing (new min > old min) → range warning appears
 * - Range narrowing (new max < old max) → range warning appears
 * - No changes → no warnings returned
 * - Description change → identifier rename warning appears
 * - Unit change on unreferenced param (hasDependent=false) → no unit warning
 */

import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { detectDependencyWarnings } from "./data-source-schema-editor";
import { DataSourceSchemaEditor } from "./data-source-schema-editor";
import type { SchemaParameter } from "./mock-schema-parameters";
import type { DataSourceRow } from "./mock-data-sources";

// ---------------------------------------------------------------------------
// Mock workspace lock so the editor is always unlocked in tests
// ---------------------------------------------------------------------------
vi.mock("../shell/mock-workspace", () => ({
  mockSourceLock: "unlocked",
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeParam(overrides: Partial<SchemaParameter> = {}): SchemaParameter {
  return {
    id: "p-test",
    name: "test.param",
    path: "test/param",
    type: "float",
    unit: "°C",
    min: 0,
    max: 100,
    description: "A test parameter",
    hasDependent: false,
    ...overrides,
  };
}

const mockSource: DataSourceRow = {
  id: "src-test",
  name: "Test Source",
  protocol: "OPC UA",
  endpoint: "opc.tcp://localhost:4840",
  parameterCount: 5,
  status: "Idle",
  health: "Healthy",
  clients: 0,
  lastOperator: "tester",
};

// ---------------------------------------------------------------------------
// Unit tests for detectDependencyWarnings helper
// ---------------------------------------------------------------------------

describe("detectDependencyWarnings — no changes", () => {
  it("returns no warnings when buffer matches original exactly", () => {
    const param = makeParam({ hasDependent: true });
    const original = { description: "A test parameter", unit: "°C", min: "0", max: "100" };
    const buffer = { ...original };
    const warnings = detectDependencyWarnings(param, buffer, original);
    expect(warnings).toHaveLength(0);
  });
});

describe("detectDependencyWarnings — unit change on referenced param", () => {
  it("returns unit warning when unit changed and param has dependents", () => {
    const param = makeParam({ hasDependent: true });
    const original = { description: "A test parameter", unit: "°C", min: "0", max: "100" };
    const buffer = { ...original, unit: "K" };
    const warnings = detectDependencyWarnings(param, buffer, original);
    expect(warnings.some((w) => w.includes("unit"))).toBe(true);
    expect(warnings.some((w) => w.includes("replay mismatches"))).toBe(true);
  });

  it("returns no unit warning when unit changed but param has no dependents", () => {
    const param = makeParam({ hasDependent: false });
    const original = { description: "A test parameter", unit: "°C", min: "0", max: "100" };
    const buffer = { ...original, unit: "K" };
    const warnings = detectDependencyWarnings(param, buffer, original);
    expect(warnings.some((w) => w.includes("replay mismatches"))).toBe(false);
  });
});

describe("detectDependencyWarnings — range narrowing", () => {
  it("returns range warning when new min is higher than original min", () => {
    const param = makeParam();
    const original = { description: "A test parameter", unit: "°C", min: "0", max: "100" };
    const buffer = { ...original, min: "10" }; // raised min → narrowing
    const warnings = detectDependencyWarnings(param, buffer, original);
    expect(warnings.some((w) => w.includes("Narrowing the value range"))).toBe(true);
  });

  it("returns range warning when new max is lower than original max", () => {
    const param = makeParam();
    const original = { description: "A test parameter", unit: "°C", min: "0", max: "100" };
    const buffer = { ...original, max: "80" }; // lowered max → narrowing
    const warnings = detectDependencyWarnings(param, buffer, original);
    expect(warnings.some((w) => w.includes("Narrowing the value range"))).toBe(true);
  });

  it("returns no range warning when range is widened", () => {
    const param = makeParam();
    const original = { description: "A test parameter", unit: "°C", min: "0", max: "100" };
    const buffer = { ...original, min: "-10", max: "200" }; // widened → no warning
    const warnings = detectDependencyWarnings(param, buffer, original);
    expect(warnings.some((w) => w.includes("Narrowing the value range"))).toBe(false);
  });
});

describe("detectDependencyWarnings — description (identifier rename)", () => {
  it("returns identifier warning when description is changed", () => {
    const param = makeParam();
    const original = { description: "Original description", unit: "°C", min: "0", max: "100" };
    const buffer = { ...original, description: "Changed description" };
    const warnings = detectDependencyWarnings(param, buffer, original);
    expect(warnings.some((w) => w.includes("identifier"))).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// Integration tests via rendered component
// ---------------------------------------------------------------------------

describe("DataSourceSchemaEditor — dependency warnings in UI", () => {
  it("shows unit warning in Dependency impact section after changing unit on referenced param", async () => {
    render(<DataSourceSchemaEditor source={mockSource} />);

    // Select first param (zone1.temp, hasDependent=true, unit=°C)
    const zoneTemp = screen.getByText("oven/zone[1]/temp");
    await userEvent.click(zoneTemp);

    // Find unit input and change its value
    const unitInputs = screen.getAllByRole("textbox");
    const unitInput = unitInputs.find(
      (el) => (el as HTMLInputElement).value === "°C",
    ) as HTMLInputElement;
    expect(unitInput).toBeTruthy();

    await userEvent.clear(unitInput);
    await userEvent.type(unitInput, "K");

    // Dependency impact section should appear
    expect(screen.getByText("Dependency impact")).toBeTruthy();
    expect(screen.getByText(/replay mismatches/)).toBeTruthy();
  });

  it("shows range narrowing warning after raising min value", async () => {
    render(<DataSourceSchemaEditor source={mockSource} />);

    // Select zone1.temp (min=-40, max=600, type=float)
    await userEvent.click(screen.getByText("oven/zone[1]/temp"));

    // Find the min input (number type, value "-40")
    const minInputs = screen.getAllByRole("spinbutton");
    const minInput = minInputs.find(
      (el) => (el as HTMLInputElement).value === "-40",
    ) as HTMLInputElement;
    expect(minInput).toBeTruthy();

    await userEvent.clear(minInput);
    await userEvent.type(minInput, "0"); // raise min from -40 to 0 → narrowing

    expect(screen.getByText("Dependency impact")).toBeTruthy();
    expect(screen.getByText(/Narrowing the value range/)).toBeTruthy();
  });

  it("shows no warnings when no changes are made", async () => {
    render(<DataSourceSchemaEditor source={mockSource} />);

    await userEvent.click(screen.getByText("oven/zone[1]/temp"));

    // No Dependency impact section without changes
    expect(screen.queryByText("Dependency impact")).toBeNull();
  });
});
