/**
 * Unit tests for the fault model (UI-063): isFaultConfigured + describeFault.
 */

import { describe, expect, it } from "vitest";
import {
  describeFault,
  faultRequiredKeys,
  isFaultConfigured,
} from "./scenario-faults";

describe("isFaultConfigured", () => {
  it("is false without a target source", () => {
    expect(isFaultConfigured({ kind: "drop", dropRate: 10 })).toBe(false);
  });

  it("is false without a kind", () => {
    expect(isFaultConfigured({ sourceId: "src-01" })).toBe(false);
  });

  it("is false when the kind's required param is missing", () => {
    expect(isFaultConfigured({ sourceId: "src-01", kind: "drop" })).toBe(false);
  });

  it("is true with target + kind + the kind's required param", () => {
    expect(isFaultConfigured({ sourceId: "src-01", kind: "drop", dropRate: 25 })).toBe(true);
    expect(isFaultConfigured({ sourceId: "src-01", kind: "delay", delayMs: 500 })).toBe(true);
    expect(isFaultConfigured({ sourceId: "src-01", kind: "quality", quality: "BAD" })).toBe(true);
  });
});

describe("faultRequiredKeys", () => {
  it("returns base keys when kind is undefined", () => {
    expect(faultRequiredKeys(undefined)).toEqual(["sourceId", "kind"]);
  });

  it("includes the kind's required params", () => {
    expect(faultRequiredKeys("delay")).toEqual(["sourceId", "kind", "delayMs"]);
  });
});

describe("describeFault", () => {
  it("prompts when no kind is chosen", () => {
    expect(describeFault({})).toMatch(/Choose a fault kind/);
  });

  it("describes a drop fault with rate and duration", () => {
    const text = describeFault({ kind: "drop", dropRate: 30, durationSeconds: 10 });
    expect(text).toMatch(/30%/);
    expect(text).toMatch(/for 10s/);
  });

  it("describes an until-cleared fault when no duration", () => {
    const text = describeFault({ kind: "delay", delayMs: 200 });
    expect(text).toMatch(/200 ms/);
    expect(text).toMatch(/until the scenario clears it/);
  });

  it("mentions the start delay when set", () => {
    const text = describeFault({ kind: "quality", quality: "BAD", startAfterSeconds: 5 });
    expect(text).toMatch(/starting 5s in/);
    expect(text).toMatch(/BAD quality/);
  });
});
