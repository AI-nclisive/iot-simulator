/**
 * Unit tests for the fault model (UI-063 / UI-130): isFaultConfigured + describeFault.
 */

import { describe, expect, it } from "vitest";
import {
  describeFault,
  faultRequiredKeys,
  isFaultConfigured,
} from "./scenario-faults";

describe("isFaultConfigured", () => {
  it("is false without a target source", () => {
    expect(isFaultConfigured({ kind: "CONNECTION_DROP" })).toBe(false);
  });

  it("is false without a kind", () => {
    expect(isFaultConfigured({ sourceId: "src-01" })).toBe(false);
  });

  it("is false when DELAY is missing its required delayMs param", () => {
    expect(isFaultConfigured({ sourceId: "src-01", kind: "DELAY" })).toBe(false);
  });

  it("is true for kinds with no required params once source + kind are set", () => {
    expect(isFaultConfigured({ sourceId: "src-01", kind: "BAD_VALUE" })).toBe(true);
    expect(isFaultConfigured({ sourceId: "src-01", kind: "MISSING_VALUE" })).toBe(true);
    expect(isFaultConfigured({ sourceId: "src-01", kind: "CONNECTION_DROP" })).toBe(true);
    expect(isFaultConfigured({ sourceId: "src-01", kind: "TIMEOUT" })).toBe(true);
    expect(isFaultConfigured({ sourceId: "src-01", kind: "PROTOCOL_ERROR" })).toBe(true);
    expect(isFaultConfigured({ sourceId: "src-01", kind: "SOURCE_UNAVAILABLE" })).toBe(true);
  });

  it("is true for DELAY when delayMs is provided", () => {
    expect(isFaultConfigured({ sourceId: "src-01", kind: "DELAY", delayMs: 500 })).toBe(true);
  });
});

describe("faultRequiredKeys", () => {
  it("returns base keys when kind is undefined", () => {
    expect(faultRequiredKeys(undefined)).toEqual(["sourceId", "kind"]);
  });

  it("includes delayMs for DELAY", () => {
    expect(faultRequiredKeys("DELAY")).toEqual(["sourceId", "kind", "delayMs"]);
  });

  it("returns only base keys for kinds with no extra params", () => {
    expect(faultRequiredKeys("BAD_VALUE")).toEqual(["sourceId", "kind"]);
    expect(faultRequiredKeys("CONNECTION_DROP")).toEqual(["sourceId", "kind"]);
  });
});

describe("describeFault", () => {
  it("prompts when no kind is chosen", () => {
    expect(describeFault({})).toMatch(/Choose a fault kind/);
  });

  it("describes BAD_VALUE with timing", () => {
    const text = describeFault({ kind: "BAD_VALUE", durationSeconds: 10 });
    expect(text).toMatch(/corrupted/i);
    expect(text).toMatch(/for 10s/);
  });

  it("describes MISSING_VALUE (drops values)", () => {
    const text = describeFault({ kind: "MISSING_VALUE" });
    expect(text).toMatch(/drops/i);
    expect(text).toMatch(/until the scenario clears it/);
  });

  it("describes DELAY with latency and duration", () => {
    const text = describeFault({ kind: "DELAY", delayMs: 200 });
    expect(text).toMatch(/200 ms/);
    expect(text).toMatch(/until the scenario clears it/);
  });

  it("describes CONNECTION_DROP", () => {
    const text = describeFault({ kind: "CONNECTION_DROP" });
    expect(text).toMatch(/connection/i);
  });

  it("describes TIMEOUT", () => {
    const text = describeFault({ kind: "TIMEOUT" });
    expect(text).toMatch(/time out/i);
  });

  it("describes PROTOCOL_ERROR", () => {
    const text = describeFault({ kind: "PROTOCOL_ERROR" });
    expect(text).toMatch(/protocol/i);
  });

  it("describes SOURCE_UNAVAILABLE", () => {
    const text = describeFault({ kind: "SOURCE_UNAVAILABLE" });
    expect(text).toMatch(/unavailable/i);
  });

  it("mentions the start delay when set", () => {
    const text = describeFault({ kind: "BAD_VALUE", startAfterSeconds: 5 });
    expect(text).toMatch(/starting 5s in/);
  });
});
