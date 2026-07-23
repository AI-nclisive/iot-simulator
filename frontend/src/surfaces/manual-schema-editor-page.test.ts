import { describe, it, expect } from "vitest";
import { formatDataType } from "./manual-schema-editor-page";

describe("formatDataType", () => {
  it("converts BOOL", () => {
    expect(formatDataType("BOOL")).toBe("Bool");
  });

  it("converts all signed integer types", () => {
    expect(formatDataType("INT8")).toBe("Int8");
    expect(formatDataType("INT16")).toBe("Int16");
    expect(formatDataType("INT32")).toBe("Int32");
    expect(formatDataType("INT64")).toBe("Int64");
  });

  it("converts all unsigned integer types", () => {
    expect(formatDataType("UINT8")).toBe("Uint8");
    expect(formatDataType("UINT16")).toBe("Uint16");
    expect(formatDataType("UINT32")).toBe("Uint32");
    expect(formatDataType("UINT64")).toBe("Uint64");
  });

  it("converts float types", () => {
    expect(formatDataType("FLOAT32")).toBe("Float32");
    expect(formatDataType("FLOAT64")).toBe("Float64");
  });

  it("converts string types", () => {
    expect(formatDataType("STRING")).toBe("String");
    expect(formatDataType("BYTES")).toBe("Bytes");
  });

  it("handles DATETIME special case", () => {
    expect(formatDataType("DATETIME")).toBe("DateTime");
  });

  it("converts underscore-separated types", () => {
    expect(formatDataType("LOCALIZED_TEXT")).toBe("LocalizedText");
    expect(formatDataType("GUID")).toBe("Guid");
    expect(formatDataType("STATUS_CODE")).toBe("StatusCode");
    expect(formatDataType("QUALIFIED_NAME")).toBe("QualifiedName");
    expect(formatDataType("NODE_ID")).toBe("NodeId");
    expect(formatDataType("EXPANDED_NODE_ID")).toBe("ExpandedNodeId");
    expect(formatDataType("XML_ELEMENT")).toBe("XmlElement");
  });

  it("produces 21 unique formatted names from all DATA_TYPES", () => {
    const DATA_TYPES = [
      "BOOL", "INT8", "UINT8", "INT16", "UINT16", "INT32", "UINT32", "INT64", "UINT64",
      "FLOAT32", "FLOAT64", "STRING", "BYTES", "DATETIME", "LOCALIZED_TEXT", "GUID",
      "STATUS_CODE", "QUALIFIED_NAME", "NODE_ID", "EXPANDED_NODE_ID", "XML_ELEMENT",
    ] as const;

    const formatted = DATA_TYPES.map(type => formatDataType(type));

    expect(formatted).toEqual([
      "Bool", "Int8", "Uint8", "Int16", "Uint16", "Int32", "Uint32", "Int64", "Uint64",
      "Float32", "Float64", "String", "Bytes", "DateTime", "LocalizedText", "Guid",
      "StatusCode", "QualifiedName", "NodeId", "ExpandedNodeId", "XmlElement",
    ]);

    expect(formatted.length).toBe(21);
    expect(new Set(formatted).size).toBe(21);
  });
});
