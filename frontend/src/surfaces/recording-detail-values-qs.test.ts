/**
 * Unit tests for buildValuesQs (UI-139)
 *
 * Covers:
 * - quality param name is "quality" (not "qualities") to match backend
 * - all-qualities selection omits the param entirely
 * - subset of qualities produces comma-separated "quality" param
 * - search, from, to params are forwarded correctly
 * - cursor param is forwarded
 */

import { describe, expect, it } from "vitest";
import { buildValuesQs } from "./recording-detail-page";

type Quality = "GOOD" | "UNCERTAIN" | "BAD";

const ALL: Quality[] = ["GOOD", "UNCERTAIN", "BAD"];

describe("buildValuesQs — quality param (UI-139)", () => {
  it("omits quality param when all three qualities are selected", () => {
    const qs = buildValuesQs({ qualities: new Set(ALL), search: "", from: "", to: "" });
    expect(qs).not.toContain("quality");
  });

  it("uses param name 'quality' (not 'qualities') for a subset", () => {
    const qs = buildValuesQs({ qualities: new Set<Quality>(["UNCERTAIN", "BAD"]), search: "", from: "", to: "" });
    expect(qs).toContain("quality=");
    expect(qs).not.toContain("qualities=");
  });

  it("excludes GOOD when only UNCERTAIN and BAD are selected", () => {
    const qs = buildValuesQs({ qualities: new Set<Quality>(["UNCERTAIN", "BAD"]), search: "", from: "", to: "" });
    const params = new URLSearchParams(qs.replace(/^\?/, ""));
    const values = params.get("quality")!.split(",");
    expect(values).toContain("UNCERTAIN");
    expect(values).toContain("BAD");
    expect(values).not.toContain("GOOD");
  });

  it("produces empty string when all qualities selected and no other filters", () => {
    const qs = buildValuesQs({ qualities: new Set(ALL), search: "", from: "", to: "" });
    expect(qs).toBe("");
  });

  it("forwards search param", () => {
    const qs = buildValuesQs({ qualities: new Set(ALL), search: "temp", from: "", to: "" });
    expect(qs).toContain("search=temp");
  });

  it("forwards cursor param", () => {
    const qs = buildValuesQs({ qualities: new Set(ALL), search: "", from: "", to: "" }, "tok-123");
    expect(qs).toContain("cursor=tok-123");
  });
});
