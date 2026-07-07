import { describe, expect, it } from "vitest";
import { neutralToUiType } from "./data-source-detail-values-tab";

describe("neutralToUiType — neutral schema type → values-tab category", () => {
  it("maps float types", () => {
    expect(neutralToUiType("FLOAT64")).toBe("float");
    expect(neutralToUiType("FLOAT32")).toBe("float");
  });

  it("maps signed and unsigned integer types", () => {
    expect(neutralToUiType("INT16")).toBe("int");
    expect(neutralToUiType("INT32")).toBe("int");
    expect(neutralToUiType("INT64")).toBe("int");
    expect(neutralToUiType("UINT16")).toBe("int");
    expect(neutralToUiType("UINT32")).toBe("int");
  });

  it("maps BOOL", () => {
    expect(neutralToUiType("BOOL")).toBe("bool");
  });

  it("falls back to string for text/bytes/datetime/unknown/null", () => {
    expect(neutralToUiType("STRING")).toBe("string");
    expect(neutralToUiType("BYTES")).toBe("string");
    expect(neutralToUiType("DATETIME")).toBe("string");
    expect(neutralToUiType(null)).toBe("string");
  });
});
