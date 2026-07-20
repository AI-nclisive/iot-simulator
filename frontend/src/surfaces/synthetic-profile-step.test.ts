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

  // IS-168 / UI-482 — structural/identifier types carry the CONSTANT's value as
  // stringValue or bytesValueBase64, not the numeric `value` field.
  it("builds CONSTANT with stringValue for a text-shaped structural type", () => {
    expect(toPattern(draft({ pattern: "CONSTANT", value: "ns=2;s=Foo" }), "NODE_ID")).toEqual({
      type: "CONSTANT",
      stringValue: "ns=2;s=Foo",
    });
    expect(toPattern(draft({ pattern: "CONSTANT", value: "not-a-real-guid" }), "GUID")).toEqual({
      type: "CONSTANT",
      stringValue: "not-a-real-guid",
    });
  });

  it("allows an empty stringValue for QUALIFIED_NAME/XML_ELEMENT but not other text types", () => {
    expect(toPattern(draft({ pattern: "CONSTANT", value: "" }), "QUALIFIED_NAME")).toEqual({
      type: "CONSTANT",
      stringValue: "",
    });
    expect(toPattern(draft({ pattern: "CONSTANT", value: "" }), "XML_ELEMENT")).toEqual({
      type: "CONSTANT",
      stringValue: "",
    });
    expect(toPattern(draft({ pattern: "CONSTANT", value: "" }), "NODE_ID")).toBeNull();
  });

  it("builds CONSTANT with bytesValueBase64 for BYTES from a hex value", () => {
    expect(toPattern(draft({ pattern: "CONSTANT", value: "010203" }), "BYTES")).toEqual({
      type: "CONSTANT",
      bytesValueBase64: "AQID",
    });
    expect(toPattern(draft({ pattern: "CONSTANT", value: "" }), "BYTES")).toEqual({
      type: "CONSTANT",
      bytesValueBase64: "",
    });
  });

  it("returns null for a BYTES value that isn't valid hex", () => {
    expect(toPattern(draft({ pattern: "CONSTANT", value: "zz" }), "BYTES")).toBeNull();
    expect(toPattern(draft({ pattern: "CONSTANT", value: "abc" }), "BYTES")).toBeNull();
  });

  it("keeps numeric CONSTANT for STATUS_CODE/DATETIME", () => {
    expect(toPattern(draft({ pattern: "CONSTANT", value: "1700000000000" }), "DATETIME")).toEqual({
      type: "CONSTANT",
      value: 1700000000000,
    });
    expect(toPattern(draft({ pattern: "CONSTANT", value: "0" }), "STATUS_CODE")).toEqual({
      type: "CONSTANT",
      value: 0,
    });
  });
});

describe("defaultDraft — CONSTANT_ONLY_TYPES locking (IS-168 / UI-482)", () => {
  it("defaults ordinary measurement types to SINE", () => {
    expect(defaultDraft("FLOAT64").pattern).toBe("SINE");
    expect(defaultDraft(null).pattern).toBe("SINE");
  });

  it("locks structural/identifier types to CONSTANT with a type-appropriate default value", () => {
    expect(defaultDraft("NODE_ID")).toMatchObject({ pattern: "CONSTANT", value: "ns=0;i=0" });
    expect(defaultDraft("STATUS_CODE")).toMatchObject({ pattern: "CONSTANT", value: "0" });
    expect(defaultDraft("BYTES")).toMatchObject({ pattern: "CONSTANT", value: "" });
    const guidDraft = defaultDraft("GUID");
    expect(guidDraft.pattern).toBe("CONSTANT");
    expect(guidDraft.value).toMatch(/^[0-9a-f-]{36}$/i);
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

  it("maps CONSTANT with stringValue (IS-168)", () => {
    expect(draftFromPattern({ type: "CONSTANT", stringValue: "ns=2;s=Foo" }, 500)).toEqual({
      pattern: "CONSTANT",
      value: "ns=2;s=Foo",
      updateRateMs: "500",
    });
  });

  it("maps CONSTANT with bytesValueBase64 (IS-168)", () => {
    expect(draftFromPattern({ type: "CONSTANT", bytesValueBase64: "AQID" }, 500)).toEqual({
      pattern: "CONSTANT",
      value: "AQID",
      updateRateMs: "500",
    });
  });
});
