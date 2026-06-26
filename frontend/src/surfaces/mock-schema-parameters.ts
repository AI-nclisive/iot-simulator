export type ParameterType = "float" | "int" | "bool" | "string";

export type SchemaParameter = {
  id: string;
  name: string;
  path: string;
  type: ParameterType;
  unit: string;
  min: number | null;
  max: number | null;
  description: string;
  hasDependent: boolean;
};

const parameters: SchemaParameter[] = [
  { id: "p-001", name: "zone1.temp", path: "oven/zone[1]/temp", type: "float", unit: "°C", min: -40, max: 600, description: "Zone 1 temperature sensor", hasDependent: true },
  { id: "p-002", name: "zone1.pressure", path: "oven/zone[1]/pressure", type: "float", unit: "bar", min: 0, max: 10, description: "Zone 1 pressure reading", hasDependent: false },
  { id: "p-003", name: "zone2.temp", path: "oven/zone[2]/temp", type: "float", unit: "°C", min: -40, max: 600, description: "Zone 2 temperature sensor", hasDependent: true },
  { id: "p-004", name: "zone2.pressure", path: "oven/zone[2]/pressure", type: "float", unit: "bar", min: 0, max: 10, description: "Zone 2 pressure reading", hasDependent: false },
  { id: "p-005", name: "conveyor.speed", path: "conveyor/speed", type: "float", unit: "m/s", min: 0, max: 5, description: "Conveyor belt speed", hasDependent: false },
  { id: "p-006", name: "conveyor.running", path: "conveyor/running", type: "bool", unit: "", min: null, max: null, description: "Conveyor active state", hasDependent: true },
  { id: "p-007", name: "inlet.valve.open", path: "inlet/valve/open", type: "bool", unit: "", min: null, max: null, description: "Inlet valve open state", hasDependent: false },
  { id: "p-008", name: "outlet.valve.open", path: "outlet/valve/open", type: "bool", unit: "", min: null, max: null, description: "Outlet valve open state", hasDependent: false },
  { id: "p-009", name: "batch.id", path: "batch/id", type: "string", unit: "", min: null, max: null, description: "Current batch identifier", hasDependent: false },
  { id: "p-010", name: "batch.count", path: "batch/count", type: "int", unit: "", min: 0, max: 9999, description: "Total batch count this session", hasDependent: false },
  { id: "p-011", name: "alarm.overheat", path: "alarm/overheat", type: "bool", unit: "", min: null, max: null, description: "Overheat alarm state", hasDependent: false },
  { id: "p-012", name: "alarm.pressure_fault", path: "alarm/pressure_fault", type: "bool", unit: "", min: null, max: null, description: "Pressure fault alarm", hasDependent: false },
];

export function getParametersForSource(_sourceId: string): SchemaParameter[] {
  return parameters;
}
