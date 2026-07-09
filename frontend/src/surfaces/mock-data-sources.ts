export type DataSourceRow = {
  id: string;
  name: string;
  protocol: "OPC UA" | "Modbus TCP";
  /** Computed simulator serve URL (e.g. opc.tcp://host:4840). Clients connect here. */
  endpoint: string;
  /** IS-127: source type — SCAN=real device, IMPORT=prepared recording, SYNTHETIC=generated, MANUAL=schema only */
  basis?: "SCAN" | "MANUAL" | "IMPORT" | "SYNTHETIC";
  /** IS-127: local simulator port (1–65535) */
  simulatorPort?: number;
  /** IS-127: real device endpoint — only set for SCAN basis */
  realDeviceEndpoint?: string | null;
  /** Raw runtime config JSON — may contain importRecordingId for IMPORT sources */
  runtimeConfig?: string | null;
  parameterCount: number;
  status: "Active" | "Stopped";
  health: "Healthy" | "Warning" | "Error";
};

export const sourceRows: DataSourceRow[] = [
  {
    id: "src-01",
    name: "Line A telemetry",
    protocol: "OPC UA",
    basis: "SCAN",
    simulatorPort: 4840,
    realDeviceEndpoint: "opc.tcp://line-a.local:4840",
    endpoint: "opc.tcp://localhost:4840",
    parameterCount: 2480,
    status: "Active",
    health: "Healthy",
  },
  {
    id: "src-02",
    name: "Packaging cell stream",
    protocol: "Modbus TCP",
    basis: "SCAN",
    simulatorPort: 502,
    realDeviceEndpoint: "10.20.4.22:502",
    endpoint: "localhost:502",
    parameterCount: 640,
    status: "Active",
    health: "Warning",
  },
  {
    id: "src-03",
    name: "Field capture telemetry",
    protocol: "OPC UA",
    basis: "IMPORT",
    simulatorPort: 4841,
    realDeviceEndpoint: null,
    endpoint: "opc.tcp://localhost:4841",
    parameterCount: 3120,
    status: "Active",
    health: "Healthy",
  },
  {
    id: "src-04",
    name: "Backup feeder stream",
    protocol: "Modbus TCP",
    basis: "MANUAL",
    simulatorPort: 503,
    realDeviceEndpoint: null,
    endpoint: "localhost:503",
    parameterCount: 128,
    status: "Stopped",
    health: "Error",
  },
];
