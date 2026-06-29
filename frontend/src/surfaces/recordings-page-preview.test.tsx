/**
 * Tests for UI-052 recording and sample preview panel
 *
 * Covers:
 * - Synthetic warning appears for synthetic-origin artifact
 * - Large-size info appears for artifact over 10 MB (10240 KB)
 * - Never-used info appears for artifact with null lastUsedAt
 * - High parameter count info appears for artifact with parameterCount > 2000
 */

import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  computeFitnessWarnings,
  FitnessWarning,
} from "./recordings-page";
import type { RecordingRow } from "./mock-recordings";

vi.mock("../shell/shell-store", () => ({
  useShellStore: vi.fn((selector: (s: { accessMode: string; sharedRole: string }) => unknown) =>
    selector({ accessMode: "local", sharedRole: "observer" }),
  ),
}));

afterEach(() => {
  cleanup();
});

// ─── Base fixture ─────────────────────────────────────────────────────────────

const baseRow: RecordingRow = {
  id: "test-001",
  name: "test-recording",
  type: "recording",
  origin: "captured",
  sourceId: "src-01",
  sourceName: "Test Source",
  protocol: "OPC UA",
  parameterCount: 100,
  duration: "1h 00m",
  capturedAt: "2026-06-24 09:00",
  capturedBy: "Tester",
  tags: [],
  lastUsedAt: "2026-06-25 10:00",
  sizeKb: 500,
};

// ─── computeFitnessWarnings unit tests ───────────────────────────────────────

describe("computeFitnessWarnings — synthetic origin", () => {
  it("emits a warn-level warning for synthetic origin", () => {
    const row: RecordingRow = { ...baseRow, origin: "synthetic" };
    const warnings = computeFitnessWarnings(row);
    const syntheticWarning = warnings.find((w) =>
      w.message.includes("Synthetic data"),
    );
    expect(syntheticWarning).toBeDefined();
    expect(syntheticWarning?.level).toBe("warn");
  });

  it("does not emit synthetic warning for captured origin", () => {
    const row: RecordingRow = { ...baseRow, origin: "captured" };
    const warnings = computeFitnessWarnings(row);
    expect(warnings.find((w) => w.message.includes("Synthetic data"))).toBeUndefined();
  });
});

describe("computeFitnessWarnings — large artifact", () => {
  it("emits an info-level warning when sizeKb > 10000", () => {
    const row: RecordingRow = { ...baseRow, sizeKb: 18400 };
    const warnings = computeFitnessWarnings(row);
    const sizeWarning = warnings.find((w) =>
      w.message.includes("Large artifact"),
    );
    expect(sizeWarning).toBeDefined();
    expect(sizeWarning?.level).toBe("info");
  });

  it("does not emit large artifact warning when sizeKb <= 10000", () => {
    const row: RecordingRow = { ...baseRow, sizeKb: 8000 };
    const warnings = computeFitnessWarnings(row);
    expect(warnings.find((w) => w.message.includes("Large artifact"))).toBeUndefined();
  });
});

describe("computeFitnessWarnings — never used", () => {
  it("emits an info-level warning when lastUsedAt is null", () => {
    const row: RecordingRow = { ...baseRow, lastUsedAt: null };
    const warnings = computeFitnessWarnings(row);
    const neverUsed = warnings.find((w) =>
      w.message.includes("Never used in replay"),
    );
    expect(neverUsed).toBeDefined();
    expect(neverUsed?.level).toBe("info");
  });
});

describe("computeFitnessWarnings — high parameter count", () => {
  it("emits an info-level warning when parameterCount > 2000", () => {
    const row: RecordingRow = { ...baseRow, parameterCount: 2480 };
    const warnings = computeFitnessWarnings(row);
    const highParam = warnings.find((w) =>
      w.message.includes("High parameter count"),
    );
    expect(highParam).toBeDefined();
    expect(highParam?.level).toBe("info");
  });
});

// ─── FitnessWarning rendering tests ──────────────────────────────────────────

describe("FitnessWarning component — synthetic warning renders in DOM", () => {
  it("renders Warning: label and synthetic message for warn level", () => {
    render(
      <FitnessWarning
        level="warn"
        message="Synthetic data — not captured from a real source. Replay behavior may differ from real-device patterns."
      />,
    );
    expect(screen.getByText("Warning:")).toBeTruthy();
    expect(
      screen.getByText(/Synthetic data — not captured from a real source/),
    ).toBeTruthy();
  });
});

describe("FitnessWarning component — large size info renders in DOM", () => {
  it("renders Note: label and large artifact message for info level", () => {
    render(
      <FitnessWarning
        level="info"
        message="Large artifact (18.0 MB) — replay may take longer to initialise."
      />,
    );
    expect(screen.getByText("Note:")).toBeTruthy();
    expect(
      screen.getByText(/Large artifact \(18\.0 MB\)/),
    ).toBeTruthy();
  });
});
