export type BackendProtocol = "OPC_UA" | "MODBUS_TCP";
export type BackendRuntimeState = "STOPPED" | "STARTING" | "RUNNING" | "ERROR" | "STALE";
export type BackendDataType =
  | "BOOL"
  | "INT16"
  | "UINT16"
  | "INT32"
  | "UINT32"
  | "INT64"
  | "UINT64"
  | "FLOAT32"
  | "FLOAT64"
  | "STRING"
  | "BYTES";

export function mapProtocol(protocol: BackendProtocol): "OPC UA" | "Modbus TCP" {
  return protocol === "OPC_UA" ? "OPC UA" : "Modbus TCP";
}

export function mapRuntimeStateToStatus(state: BackendRuntimeState): "Active" | "Stopped" {
  return state === "RUNNING" ? "Active" : "Stopped";
}

export function mapRuntimeStateToHealth(
  state: BackendRuntimeState,
): "Healthy" | "Warning" | "Error" | null {
  if (state === "RUNNING") return "Healthy";
  if (state === "STALE") return "Warning";
  if (state === "ERROR") return "Error";
  return null;
}

export function mapDataType(
  dataType: BackendDataType,
): "float" | "int" | "bool" | "string" | null {
  if (dataType === "FLOAT32" || dataType === "FLOAT64") return "float";
  if (
    dataType === "INT16" ||
    dataType === "UINT16" ||
    dataType === "INT32" ||
    dataType === "UINT32" ||
    dataType === "INT64" ||
    dataType === "UINT64"
  )
    return "int";
  if (dataType === "BOOL") return "bool";
  if (dataType === "STRING") return "string";
  return null; // BYTES — no FE type yet
}
