import { describe, expect, it } from "vitest";
import {
  mapDataType,
  mapProtocol,
  mapRuntimeStateToHealth,
  mapRuntimeStateToStatus,
} from "./mappers";

describe("mapProtocol", () => {
  it("maps OPC_UA to OPC UA", () => {
    expect(mapProtocol("OPC_UA")).toBe("OPC UA");
  });
  it("maps MODBUS_TCP to Modbus TCP", () => {
    expect(mapProtocol("MODBUS_TCP")).toBe("Modbus TCP");
  });
});

describe("mapRuntimeStateToStatus", () => {
  it("maps RUNNING to Active", () => {
    expect(mapRuntimeStateToStatus("RUNNING")).toBe("Active");
  });
  it.each(["STOPPED", "STARTING", "ERROR", "STALE"] as const)(
    "maps %s to Stopped",
    (state) => {
      expect(mapRuntimeStateToStatus(state)).toBe("Stopped");
    },
  );
});

describe("mapRuntimeStateToHealth", () => {
  it("maps RUNNING to Healthy", () => {
    expect(mapRuntimeStateToHealth("RUNNING")).toBe("Healthy");
  });
  it("maps STALE to Warning", () => {
    expect(mapRuntimeStateToHealth("STALE")).toBe("Warning");
  });
  it("maps ERROR to Error", () => {
    expect(mapRuntimeStateToHealth("ERROR")).toBe("Error");
  });
  it.each(["STOPPED", "STARTING"] as const)("maps %s to null", (state) => {
    expect(mapRuntimeStateToHealth(state)).toBeNull();
  });
});

describe("mapDataType", () => {
  it.each(["FLOAT32", "FLOAT64"] as const)("maps %s to float", (dt) => {
    expect(mapDataType(dt)).toBe("float");
  });
  it.each(["INT16", "UINT16", "INT32", "UINT32", "INT64", "UINT64"] as const)(
    "maps %s to int",
    (dt) => {
      expect(mapDataType(dt)).toBe("int");
    },
  );
  it("maps BOOL to bool", () => {
    expect(mapDataType("BOOL")).toBe("bool");
  });
  it("maps STRING to string", () => {
    expect(mapDataType("STRING")).toBe("string");
  });
  it("maps BYTES to null", () => {
    expect(mapDataType("BYTES")).toBeNull();
  });
});
