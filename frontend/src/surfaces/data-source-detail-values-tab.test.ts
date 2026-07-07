import { describe, expect, it } from "vitest";
import { applyPinnedIds, neutralToUiType, nodeIdFromRowId } from "./data-source-detail-values-tab";
import type { SourceValueRow } from "./mock-source-values";

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

describe("nodeIdFromRowId", () => {
  it("extracts the nodeId portion after sourceId:", () => {
    expect(nodeIdFromRowId("src-01:ns=2;s=Temperature", "src-01")).toBe(
      "ns=2;s=Temperature",
    );
  });

  it("handles nodeIds that contain colons", () => {
    expect(nodeIdFromRowId("src-99:ns=3;g=a:b:c", "src-99")).toBe("ns=3;g=a:b:c");
  });
});

describe("applyPinnedIds — pin state applied to rows", () => {
  function makeRow(nodeId: string, pinned = false): SourceValueRow {
    return {
      id: `src-01:${nodeId}`,
      sourceId: "src-01",
      path: nodeId,
      dataType: "float",
      currentValue: "1.0",
      updatedAt: "10:00:00",
      freshness: "Live",
      pinned,
    };
  }

  it("sets pinned=true for rows whose nodeId is in pinnedIds", () => {
    const rows = [makeRow("alpha"), makeRow("beta"), makeRow("gamma")];
    const result = applyPinnedIds(rows, new Set(["beta"]));
    expect(result.find((r) => r.path === "beta")?.pinned).toBe(true);
    expect(result.find((r) => r.path === "alpha")?.pinned).toBe(false);
    expect(result.find((r) => r.path === "gamma")?.pinned).toBe(false);
  });

  it("keeps pinned=false for rows not in pinnedIds", () => {
    const rows = [makeRow("alpha", true), makeRow("beta")];
    const result = applyPinnedIds(rows, new Set([]));
    expect(result.every((r) => !r.pinned)).toBe(true);
  });

  it("sorts pinned rows to the top, unpinned rows after", () => {
    const rows = [makeRow("a"), makeRow("b"), makeRow("c"), makeRow("d")];
    const result = applyPinnedIds(rows, new Set(["c", "a"]));
    const paths = result.map((r) => r.path);
    // pinned first (a and c appear before b and d)
    expect(paths.indexOf("a")).toBeLessThan(paths.indexOf("b"));
    expect(paths.indexOf("c")).toBeLessThan(paths.indexOf("b"));
    expect(paths.indexOf("a")).toBeLessThan(paths.indexOf("d"));
    expect(paths.indexOf("c")).toBeLessThan(paths.indexOf("d"));
  });

  it("preserves relative order within pinned group", () => {
    const rows = [makeRow("x"), makeRow("y"), makeRow("z")];
    const result = applyPinnedIds(rows, new Set(["x", "z"]));
    const paths = result.map((r) => r.path);
    // x comes before z in original order — must remain so in pinned group
    expect(paths.indexOf("x")).toBeLessThan(paths.indexOf("z"));
  });

  it("preserves relative order within unpinned group", () => {
    const rows = [makeRow("a"), makeRow("b"), makeRow("c")];
    const result = applyPinnedIds(rows, new Set(["a"]));
    const paths = result.map((r) => r.path);
    // b and c are both unpinned; b came before c originally
    expect(paths.indexOf("b")).toBeLessThan(paths.indexOf("c"));
  });

  it("returns original row reference when pinned state is unchanged", () => {
    const row = makeRow("alpha");
    const result = applyPinnedIds([row], new Set([]));
    // Same reference — no unnecessary object allocation.
    expect(result[0]).toBe(row);
  });

  it("survives an SSE snapshot by re-applying pinnedIds to new rows", () => {
    // Simulate: row was pinned, then a snapshot arrives with fresh rows (pinned=false).
    const snapshotRows = [makeRow("temp"), makeRow("pressure")];
    const pinnedIds = new Set(["pressure"]);
    const result = applyPinnedIds(snapshotRows, pinnedIds);
    expect(result[0].path).toBe("pressure");
    expect(result[0].pinned).toBe(true);
    expect(result[1].path).toBe("temp");
    expect(result[1].pinned).toBe(false);
  });

  it("handles an empty pinnedIds set — no rows become pinned", () => {
    const rows = [makeRow("a", true), makeRow("b")];
    const result = applyPinnedIds(rows, new Set());
    expect(result.every((r) => !r.pinned)).toBe(true);
  });

  it("handles empty rows array", () => {
    expect(applyPinnedIds([], new Set(["x"]))).toEqual([]);
  });
});
