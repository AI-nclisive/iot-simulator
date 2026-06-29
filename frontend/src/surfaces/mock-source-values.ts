export type SourceValueRow = {
  id: string;
  sourceId: string;
  path: string;
  dataType: "float" | "int" | "bool" | "string";
  currentValue: string;
  updatedAt: string;
  freshness: "Live" | "No updates";
  pinned: boolean;
};

const staticRows: SourceValueRow[] = [
  { id: "val-01", sourceId: "src-01", path: "oven.zone[1].temp", dataType: "float", currentValue: "182.4 C", updatedAt: "09:41:12", freshness: "Live", pinned: true },
  { id: "val-02", sourceId: "src-01", path: "oven.zone[2].temp", dataType: "float", currentValue: "183.1 C", updatedAt: "09:41:10", freshness: "Live", pinned: false },
  { id: "val-03", sourceId: "src-01", path: "line.speed", dataType: "float", currentValue: "1.82 m/s", updatedAt: "09:41:11", freshness: "Live", pinned: true },
  { id: "val-04", sourceId: "src-01", path: "line.batch.id", dataType: "string", currentValue: "BATCH-441", updatedAt: "09:40:58", freshness: "Live", pinned: false },
  { id: "val-05", sourceId: "src-01", path: "alarm.state", dataType: "string", currentValue: "Clear", updatedAt: "09:40:55", freshness: "Live", pinned: true },
  { id: "val-06", sourceId: "src-01", path: "power.feed.current", dataType: "float", currentValue: "12.8 A", updatedAt: "09:40:16", freshness: "No updates", pinned: false },
  { id: "val-07", sourceId: "src-01", path: "quality.reject.count", dataType: "int", currentValue: "4", updatedAt: "09:39:48", freshness: "No updates", pinned: false },
  { id: "val-08", sourceId: "src-02", path: "register[120].state", dataType: "string", currentValue: "Running", updatedAt: "09:41:02", freshness: "Live", pinned: true },
  { id: "val-09", sourceId: "src-02", path: "line.counter", dataType: "int", currentValue: "14,882", updatedAt: "09:41:00", freshness: "Live", pinned: true },
  { id: "val-10", sourceId: "src-02", path: "jam.warning", dataType: "bool", currentValue: "false", updatedAt: "09:40:59", freshness: "Live", pinned: false },
  { id: "val-11", sourceId: "src-02", path: "register[131].pressure", dataType: "float", currentValue: "4.8 bar", updatedAt: "09:40:12", freshness: "No updates", pinned: false },
  { id: "val-12", sourceId: "src-02", path: "motor.load.avg", dataType: "float", currentValue: "62 %", updatedAt: "09:40:09", freshness: "No updates", pinned: false },
  { id: "val-13", sourceId: "src-03", path: "pump[2].rpm", dataType: "int", currentValue: "1,420", updatedAt: "09:41:06", freshness: "Live", pinned: true },
  { id: "val-14", sourceId: "src-03", path: "batch.temp.avg", dataType: "float", currentValue: "74.2 C", updatedAt: "09:41:05", freshness: "Live", pinned: true },
  { id: "val-15", sourceId: "src-03", path: "quality.flag", dataType: "string", currentValue: "Review", updatedAt: "09:41:04", freshness: "Live", pinned: true },
  { id: "val-16", sourceId: "src-03", path: "pump[2].state", dataType: "string", currentValue: "Running", updatedAt: "09:40:58", freshness: "Live", pinned: false },
  { id: "val-17", sourceId: "src-03", path: "flow.m3h", dataType: "float", currentValue: "11.4", updatedAt: "09:39:52", freshness: "No updates", pinned: false },
  { id: "val-18", sourceId: "src-04", path: "backup.feed.enabled", dataType: "bool", currentValue: "false", updatedAt: "09:12:20", freshness: "No updates", pinned: true },
  { id: "val-19", sourceId: "src-04", path: "backup.feed.current", dataType: "float", currentValue: "0.0 A", updatedAt: "09:12:20", freshness: "No updates", pinned: false },
];

const generatedPaths: Array<{ path: string; dataType: SourceValueRow["dataType"]; unit: string }> = [
  { path: "oven.zone[3].temp", dataType: "float", unit: "C" },
  { path: "oven.zone[4].temp", dataType: "float", unit: "C" },
  { path: "oven.zone[5].temp", dataType: "float", unit: "C" },
  { path: "oven.zone[6].temp", dataType: "float", unit: "C" },
  { path: "oven.zone[1].pressure", dataType: "float", unit: "bar" },
  { path: "oven.zone[2].pressure", dataType: "float", unit: "bar" },
  { path: "oven.zone[3].pressure", dataType: "float", unit: "bar" },
  { path: "conveyor.speed", dataType: "float", unit: "m/s" },
  { path: "conveyor.running", dataType: "bool", unit: "" },
  { path: "conveyor.load", dataType: "float", unit: "%" },
  { path: "conveyor.tension", dataType: "float", unit: "N" },
  { path: "inlet.valve.open", dataType: "bool", unit: "" },
  { path: "inlet.valve.pos", dataType: "float", unit: "%" },
  { path: "outlet.valve.open", dataType: "bool", unit: "" },
  { path: "outlet.valve.pos", dataType: "float", unit: "%" },
  { path: "batch.count", dataType: "int", unit: "" },
  { path: "batch.cycle.time", dataType: "float", unit: "s" },
  { path: "batch.weight", dataType: "float", unit: "kg" },
  { path: "alarm.overheat", dataType: "bool", unit: "" },
  { path: "alarm.pressure_fault", dataType: "bool", unit: "" },
  { path: "alarm.estop", dataType: "bool", unit: "" },
  { path: "alarm.door.open", dataType: "bool", unit: "" },
  { path: "motor.rpm", dataType: "int", unit: "rpm" },
  { path: "motor.torque", dataType: "float", unit: "Nm" },
  { path: "motor.current", dataType: "float", unit: "A" },
  { path: "motor.temp", dataType: "float", unit: "C" },
  { path: "motor.vibration", dataType: "float", unit: "mm/s" },
  { path: "coolant.flow", dataType: "float", unit: "L/min" },
  { path: "coolant.temp", dataType: "float", unit: "C" },
  { path: "coolant.level", dataType: "float", unit: "%" },
  { path: "coolant.ph", dataType: "float", unit: "" },
  { path: "pump.active", dataType: "bool", unit: "" },
  { path: "pump.pressure", dataType: "float", unit: "bar" },
  { path: "pump.flow", dataType: "float", unit: "L/min" },
  { path: "pump.rpm", dataType: "int", unit: "rpm" },
  { path: "feeder.rate", dataType: "float", unit: "kg/h" },
  { path: "feeder.level", dataType: "float", unit: "%" },
  { path: "feeder.running", dataType: "bool", unit: "" },
  { path: "feeder.jam", dataType: "bool", unit: "" },
  { path: "exhaust.flow", dataType: "float", unit: "m3/h" },
  { path: "exhaust.temp", dataType: "float", unit: "C" },
  { path: "exhaust.fan.speed", dataType: "int", unit: "rpm" },
  { path: "exhaust.filter.dp", dataType: "float", unit: "Pa" },
  { path: "power.consumption", dataType: "float", unit: "kW" },
  { path: "power.factor", dataType: "float", unit: "" },
  { path: "power.voltage", dataType: "float", unit: "V" },
  { path: "power.frequency", dataType: "float", unit: "Hz" },
  { path: "session.uptime", dataType: "int", unit: "s" },
  { path: "session.cycle_count", dataType: "int", unit: "" },
  { path: "session.error_count", dataType: "int", unit: "" },
  { path: "sensor.humidity", dataType: "float", unit: "%" },
  { path: "sensor.ambient.temp", dataType: "float", unit: "C" },
  { path: "sensor.vibration.x", dataType: "float", unit: "g" },
  { path: "sensor.vibration.y", dataType: "float", unit: "g" },
  { path: "sensor.vibration.z", dataType: "float", unit: "g" },
  { path: "plc.scan.time", dataType: "float", unit: "ms" },
  { path: "plc.cycle.count", dataType: "int", unit: "" },
  { path: "plc.io.faults", dataType: "int", unit: "" },
  { path: "safety.relay[1].ok", dataType: "bool", unit: "" },
  { path: "safety.relay[2].ok", dataType: "bool", unit: "" },
  { path: "safety.estop.active", dataType: "bool", unit: "" },
  { path: "safety.light.curtain", dataType: "bool", unit: "" },
  { path: "vision.part.ok", dataType: "bool", unit: "" },
  { path: "vision.defect.count", dataType: "int", unit: "" },
  { path: "vision.fps", dataType: "float", unit: "" },
  { path: "hmi.active.screen", dataType: "string", unit: "" },
  { path: "hmi.operator.id", dataType: "string", unit: "" },
  { path: "hmi.last.ack", dataType: "string", unit: "" },
  { path: "servo[1].pos", dataType: "float", unit: "mm" },
  { path: "servo[1].vel", dataType: "float", unit: "mm/s" },
  { path: "servo[1].torque", dataType: "float", unit: "%" },
  { path: "servo[2].pos", dataType: "float", unit: "mm" },
  { path: "servo[2].vel", dataType: "float", unit: "mm/s" },
  { path: "servo[2].torque", dataType: "float", unit: "%" },
  { path: "servo[3].pos", dataType: "float", unit: "mm" },
  { path: "servo[3].vel", dataType: "float", unit: "mm/s" },
  { path: "servo[3].torque", dataType: "float", unit: "%" },
  { path: "robot.arm.state", dataType: "string", unit: "" },
  { path: "robot.arm.speed", dataType: "float", unit: "%" },
  { path: "robot.arm.pos.x", dataType: "float", unit: "mm" },
  { path: "robot.arm.pos.y", dataType: "float", unit: "mm" },
  { path: "robot.arm.pos.z", dataType: "float", unit: "mm" },
  { path: "robot.gripper.closed", dataType: "bool", unit: "" },
  { path: "robot.gripper.force", dataType: "float", unit: "N" },
  { path: "tank[1].level", dataType: "float", unit: "%" },
  { path: "tank[1].temp", dataType: "float", unit: "C" },
  { path: "tank[1].pressure", dataType: "float", unit: "bar" },
  { path: "tank[2].level", dataType: "float", unit: "%" },
  { path: "tank[2].temp", dataType: "float", unit: "C" },
  { path: "tank[2].pressure", dataType: "float", unit: "bar" },
  { path: "network.latency", dataType: "float", unit: "ms" },
  { path: "network.packet.loss", dataType: "float", unit: "%" },
  { path: "network.connected", dataType: "bool", unit: "" },
  { path: "opc.session.count", dataType: "int", unit: "" },
  { path: "opc.publish.rate", dataType: "float", unit: "Hz" },
];

const sampleValues: Record<SourceValueRow["dataType"], string[]> = {
  float: ["0.0", "1.2", "3.7", "12.4", "48.9", "72.1", "100.0", "182.4", "210.5"],
  int: ["0", "1", "4", "12", "88", "441", "1420", "3000", "14882"],
  bool: ["true", "false"],
  string: ["Running", "Idle", "Stopped", "Error", "Ready", "Active"],
};

const times = ["09:38:01", "09:39:12", "09:39:48", "09:40:05", "09:40:16", "09:40:44", "09:41:00", "09:41:08", "09:41:12"];

function pickFrom<T>(arr: T[], seed: number): T {
  return arr[seed % arr.length];
}

const generatedRows: SourceValueRow[] = generatedPaths.map((spec, i) => {
  const seed = i + 20;
  const values = sampleValues[spec.dataType];
  const rawVal = pickFrom(values, seed * 7);
  const value = spec.unit ? `${rawVal} ${spec.unit}` : rawVal;
  const isLive = seed % 3 !== 0;
  return {
    id: `val-${String(seed).padStart(3, "0")}`,
    sourceId: "src-01",
    path: spec.path,
    dataType: spec.dataType,
    currentValue: value,
    updatedAt: pickFrom(times, seed * 3),
    freshness: isLive ? "Live" : "No updates",
    pinned: false,
  };
});

export const sourceValueRows: SourceValueRow[] = [...staticRows, ...generatedRows];
