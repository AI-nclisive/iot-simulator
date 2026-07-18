export type BackendProtocol = "OPC_UA" | "MODBUS_TCP";
export type BackendRuntimeState = "STOPPED" | "STARTING" | "RUNNING" | "ERROR" | "STALE";
export type BackendDataType =
  | "BOOL"
  | "INT8"
  | "UINT8"
  | "INT16"
  | "UINT16"
  | "INT32"
  | "UINT32"
  | "INT64"
  | "UINT64"
  | "FLOAT32"
  | "FLOAT64"
  | "STRING"
  | "BYTES"
  | "LOCALIZED_TEXT"
  | "GUID"
  | "STATUS_CODE"
  | "QUALIFIED_NAME"
  | "NODE_ID"
  | "EXPANDED_NODE_ID"
  | "XML_ELEMENT";

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
    dataType === "INT8" ||
    dataType === "UINT8" ||
    dataType === "INT16" ||
    dataType === "UINT16" ||
    dataType === "INT32" ||
    dataType === "UINT32" ||
    dataType === "INT64" ||
    dataType === "UINT64" ||
    dataType === "STATUS_CODE"
  )
    return "int";
  if (dataType === "BOOL") return "bool";
  if (
    dataType === "STRING" ||
    dataType === "LOCALIZED_TEXT" ||
    dataType === "GUID" ||
    dataType === "QUALIFIED_NAME" ||
    dataType === "NODE_ID" ||
    dataType === "EXPANDED_NODE_ID" ||
    dataType === "XML_ELEMENT"
  )
    return "string";
  return null; // BYTES — no FE type yet
}
