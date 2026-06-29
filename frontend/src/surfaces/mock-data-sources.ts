export type DataSourceRow = {
  id: string;
  name: string;
  protocol: "OPC UA" | "Modbus TCP";
  endpoint: string;
  parameterCount: number;
  status: "Active" | "Stopped";
  health: "Healthy" | "Warning" | "Error";
};

export const sourceRows: DataSourceRow[] = [
  {
    id: "src-01",
    name: "Line A telemetry",
    protocol: "OPC UA",
    endpoint: "opc.tcp://line-a.local:4840",
    parameterCount: 2480,
    status: "Active",
    health: "Healthy",
  },
  {
    id: "src-02",
    name: "Packaging cell stream",
    protocol: "Modbus TCP",
    endpoint: "10.20.4.22:502",
    parameterCount: 640,
    status: "Active",
    health: "Warning",
  },
  {
    id: "src-03",
    name: "Field capture telemetry",
    protocol: "OPC UA",
    endpoint: "opc.tcp://field-lab.local:4801",
    parameterCount: 3120,
    status: "Active",
    health: "Healthy",
  },
  {
    id: "src-04",
    name: "Backup feeder stream",
    protocol: "Modbus TCP",
    endpoint: "10.20.4.51:502",
    parameterCount: 128,
    status: "Stopped",
    health: "Error",
  },
];
