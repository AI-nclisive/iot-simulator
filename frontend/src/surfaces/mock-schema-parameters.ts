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
  { id: "p-013", name: "zone3.temp", path: "oven/zone[3]/temp", type: "float", unit: "°C", min: -40, max: 600, description: "Zone 3 temperature sensor", hasDependent: false },
  { id: "p-014", name: "zone3.pressure", path: "oven/zone[3]/pressure", type: "float", unit: "bar", min: 0, max: 10, description: "Zone 3 pressure reading", hasDependent: false },
  { id: "p-015", name: "motor.rpm", path: "motor/rpm", type: "int", unit: "rpm", min: 0, max: 3000, description: "Main motor speed", hasDependent: true },
  { id: "p-016", name: "motor.torque", path: "motor/torque", type: "float", unit: "Nm", min: 0, max: 500, description: "Motor torque output", hasDependent: false },
  { id: "p-017", name: "motor.current", path: "motor/current", type: "float", unit: "A", min: 0, max: 100, description: "Motor current draw", hasDependent: false },
  { id: "p-018", name: "coolant.flow", path: "coolant/flow", type: "float", unit: "L/min", min: 0, max: 50, description: "Coolant flow rate", hasDependent: false },
  { id: "p-019", name: "coolant.temp", path: "coolant/temp", type: "float", unit: "°C", min: 5, max: 80, description: "Coolant temperature", hasDependent: false },
  { id: "p-020", name: "coolant.level", path: "coolant/level", type: "float", unit: "%", min: 0, max: 100, description: "Coolant reservoir level", hasDependent: false },
  { id: "p-021", name: "pump.active", path: "pump/active", type: "bool", unit: "", min: null, max: null, description: "Pump running state", hasDependent: false },
  { id: "p-022", name: "pump.pressure", path: "pump/pressure", type: "float", unit: "bar", min: 0, max: 15, description: "Pump output pressure", hasDependent: false },
  { id: "p-023", name: "feeder.rate", path: "feeder/rate", type: "float", unit: "kg/h", min: 0, max: 200, description: "Material feed rate", hasDependent: false },
  { id: "p-024", name: "feeder.level", path: "feeder/level", type: "float", unit: "%", min: 0, max: 100, description: "Feeder hopper fill level", hasDependent: true },
  { id: "p-025", name: "exhaust.flow", path: "exhaust/flow", type: "float", unit: "m³/h", min: 0, max: 1000, description: "Exhaust ventilation flow", hasDependent: false },
  { id: "p-026", name: "exhaust.temp", path: "exhaust/temp", type: "float", unit: "°C", min: 0, max: 300, description: "Exhaust gas temperature", hasDependent: false },
  { id: "p-027", name: "power.consumption", path: "power/consumption", type: "float", unit: "kW", min: 0, max: 500, description: "Total power draw", hasDependent: false },
  { id: "p-028", name: "power.factor", path: "power/factor", type: "float", unit: "", min: 0, max: 1, description: "Power factor reading", hasDependent: false },
  { id: "p-029", name: "session.uptime", path: "session/uptime", type: "int", unit: "s", min: 0, max: null, description: "Session uptime in seconds", hasDependent: false },
  { id: "p-030", name: "session.cycle_count", path: "session/cycle_count", type: "int", unit: "", min: 0, max: null, description: "Completed cycles this session", hasDependent: false },
];

export function getParametersForSource(_sourceId: string): SchemaParameter[] {
  return parameters;
}
