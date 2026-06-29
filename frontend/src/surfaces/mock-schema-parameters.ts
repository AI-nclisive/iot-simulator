export type ParameterType = "float" | "int" | "bool" | "string";

export type SchemaParameter = {
  id: string;
  name: string;
  path: string;
  type: ParameterType;
  unit: string;
  description: string;
};

const parameters: SchemaParameter[] = [
  { id: "p-001", name: "zone1.temp", path: "oven/zone[1]/temp", type: "float", unit: "°C", description: "Zone 1 temperature sensor" },
  { id: "p-002", name: "zone1.pressure", path: "oven/zone[1]/pressure", type: "float", unit: "bar", description: "Zone 1 pressure reading" },
  { id: "p-003", name: "zone2.temp", path: "oven/zone[2]/temp", type: "float", unit: "°C", description: "Zone 2 temperature sensor" },
  { id: "p-004", name: "zone2.pressure", path: "oven/zone[2]/pressure", type: "float", unit: "bar", description: "Zone 2 pressure reading" },
  { id: "p-005", name: "conveyor.speed", path: "conveyor/speed", type: "float", unit: "m/s", description: "Conveyor belt speed" },
  { id: "p-006", name: "conveyor.running", path: "conveyor/running", type: "bool", unit: "", description: "Conveyor active state" },
  { id: "p-007", name: "inlet.valve.open", path: "inlet/valve/open", type: "bool", unit: "", description: "Inlet valve open state" },
  { id: "p-008", name: "outlet.valve.open", path: "outlet/valve/open", type: "bool", unit: "", description: "Outlet valve open state" },
  { id: "p-009", name: "batch.id", path: "batch/id", type: "string", unit: "", description: "Current batch identifier" },
  { id: "p-010", name: "batch.count", path: "batch/count", type: "int", unit: "", description: "Total batch count this session" },
  { id: "p-011", name: "alarm.overheat", path: "alarm/overheat", type: "bool", unit: "", description: "Overheat alarm state" },
  { id: "p-012", name: "alarm.pressure_fault", path: "alarm/pressure_fault", type: "bool", unit: "", description: "Pressure fault alarm" },
  { id: "p-013", name: "zone3.temp", path: "oven/zone[3]/temp", type: "float", unit: "°C", description: "Zone 3 temperature sensor" },
  { id: "p-014", name: "zone3.pressure", path: "oven/zone[3]/pressure", type: "float", unit: "bar", description: "Zone 3 pressure reading" },
  { id: "p-015", name: "motor.rpm", path: "motor/rpm", type: "int", unit: "rpm", description: "Main motor speed" },
  { id: "p-016", name: "motor.torque", path: "motor/torque", type: "float", unit: "Nm", description: "Motor torque output" },
  { id: "p-017", name: "motor.current", path: "motor/current", type: "float", unit: "A", description: "Motor current draw" },
  { id: "p-018", name: "coolant.flow", path: "coolant/flow", type: "float", unit: "L/min", description: "Coolant flow rate" },
  { id: "p-019", name: "coolant.temp", path: "coolant/temp", type: "float", unit: "°C", description: "Coolant temperature" },
  { id: "p-020", name: "coolant.level", path: "coolant/level", type: "float", unit: "%", description: "Coolant reservoir level" },
  { id: "p-021", name: "pump.active", path: "pump/active", type: "bool", unit: "", description: "Pump running state" },
  { id: "p-022", name: "pump.pressure", path: "pump/pressure", type: "float", unit: "bar", description: "Pump output pressure" },
  { id: "p-023", name: "feeder.rate", path: "feeder/rate", type: "float", unit: "kg/h", description: "Material feed rate" },
  { id: "p-024", name: "feeder.level", path: "feeder/level", type: "float", unit: "%", description: "Feeder hopper fill level" },
  { id: "p-025", name: "exhaust.flow", path: "exhaust/flow", type: "float", unit: "m³/h", description: "Exhaust ventilation flow" },
  { id: "p-026", name: "exhaust.temp", path: "exhaust/temp", type: "float", unit: "°C", description: "Exhaust gas temperature" },
  { id: "p-027", name: "power.consumption", path: "power/consumption", type: "float", unit: "kW", description: "Total power draw" },
  { id: "p-028", name: "power.factor", path: "power/factor", type: "float", unit: "", description: "Power factor reading" },
  { id: "p-029", name: "session.uptime", path: "session/uptime", type: "int", unit: "s", description: "Session uptime in seconds" },
  { id: "p-030", name: "session.cycle_count", path: "session/cycle_count", type: "int", unit: "", description: "Completed cycles this session" },
];

export function getParametersForSource(_sourceId: string): SchemaParameter[] {
  return parameters;
}
