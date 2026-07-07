import { describe, expect, it } from "vitest";
import { defaultDraft, draftFromPattern, toPattern, type NodeDraft } from "./synthetic-profile-step";

function draft(overrides: Partial<NodeDraft>): NodeDraft {
  return { ...defaultDraft(), ...overrides };
}

describe("toPattern — draft → serialized pattern", () => {
  it("builds CONSTANT from value", () => {
    expect(toPattern(draft({ pattern: "CONSTANT", value: "5" }))).toEqual({ type: "CONSTANT", value: 5 });
  });

  it("builds RANDOM_UNIFORM from min/max", () => {
    expect(toPattern(draft({ pattern: "RANDOM_UNIFORM", min: "1", max: "9" }))).toEqual({
      type: "RANDOM_UNIFORM",
      min: 1,
      max: 9,
    });
  });

  it("builds RANDOM_WALK from min/max/volatility", () => {
    expect(toPattern(draft({ pattern: "RANDOM_WALK", min: "0", max: "10", volatility: "2" }))).toEqual({
      type: "RANDOM_WALK",
      min: 0,
      max: 10,
      volatility: 2,
    });
  });

  it("builds SINE from min/max/period", () => {
    expect(toPattern(draft({ pattern: "SINE", min: "20", max: "80", periodMs: "1000" }))).toEqual({
      type: "SINE",
      min: 20,
      max: 80,
      periodMs: 1000,
    });
  });

  it("returns null when min > max", () => {
    expect(toPattern(draft({ pattern: "RANDOM_UNIFORM", min: "9", max: "1" }))).toBeNull();
  });

  it("returns null for a non-positive period", () => {
    expect(toPattern(draft({ pattern: "SINE", min: "0", max: "1", periodMs: "0" }))).toBeNull();
  });

  it("returns null for a negative volatility", () => {
    expect(toPattern(draft({ pattern: "RANDOM_WALK", min: "0", max: "10", volatility: "-1" }))).toBeNull();
  });

  it("returns null for empty numeric fields", () => {
    expect(toPattern(draft({ pattern: "CONSTANT", value: "" }))).toBeNull();
    expect(toPattern(draft({ pattern: "SINE", min: "", max: "1", periodMs: "10" }))).toBeNull();
  });
});

describe("draftFromPattern — suggested pattern → draft fields", () => {
  it("maps CONSTANT", () => {
    expect(draftFromPattern({ type: "CONSTANT", value: 7 }, 500)).toEqual({
      pattern: "CONSTANT",
      value: "7",
      updateRateMs: "500",
    });
  });

  it("maps RANDOM_WALK", () => {
    expect(draftFromPattern({ type: "RANDOM_WALK", min: 0, max: 10, volatility: 2 }, 1000)).toEqual({
      pattern: "RANDOM_WALK",
      min: "0",
      max: "10",
      volatility: "2",
      updateRateMs: "1000",
    });
  });

  it("maps SINE with period", () => {
    expect(draftFromPattern({ type: "SINE", min: 8, max: 104, periodMs: 10000 }, 1000)).toEqual({
      pattern: "SINE",
      min: "8",
      max: "104",
      periodMs: "10000",
      updateRateMs: "1000",
    });
  });
});
