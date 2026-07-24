# OPC UA Device Templates Guide

Complete reference for the 15 standard device templates available in the manual schema editor. Each template is a reusable, production-ready structure with proper VARIABLE hierarchy, Property/Component children, and OPC UA attributes per IEC 62541.

## Overview

Templates are organized in 4 categories:
- **Industrial Devices** (5): Real equipment commonly found in manufacturing
- **Standards** (3): Industry-standard patterns and device metadata
- **Sensors** (4): Common measurement instruments with metadata
- **Advanced Systems** (3): Multi-component hierarchies for complex installations

## Quick Start

1. Open manual schema editor at `/manual-schemas`
2. Click **"Add from template"** button in toolbar
3. Search/select a template (e.g., "Pump")
4. Template nodes automatically added to your schema
5. Further customize individual nodes as needed
6. Save schema with template + custom nodes

---

## Industrial Devices

### 1. Pump

**Purpose**: Simulate a centrifugal or reciprocating pump with motor coupling.

**Structure**:
```
Pump (VARIABLE parent)
├── FlowRate (FLOAT64, HAS_PROPERTY)
├── Pressure (FLOAT64, HAS_PROPERTY)
├── Status (INT32, HAS_PROPERTY)
├── IsRunning (BOOL, HAS_PROPERTY)
└── Motor (OBJECT, HAS_COMPONENT)
    ├── Speed (FLOAT64)
    ├── Current (FLOAT64)
    ├── Temperature (FLOAT64)
    └── Mode (INT32)
```

**Attributes**:
- `FlowRate`: minimumSamplingInterval=500ms, historizing=true
- `Pressure`: minimumSamplingInterval=500ms, historizing=true
- `Status`: accessLevelFull=3 (READ_WRITE)

**Use Case**: Industrial pumping systems, HVAC circulation, coolant recirculation

**Customization**: Adjust property names for specific pump types (gear pump, screw pump); add Efficiency or PowerConsumption variables

---

### 2. Motor

**Purpose**: Standalone electric motor with speed, current, and thermal monitoring.

**Structure**:
```
Motor (VARIABLE parent)
├── Speed (FLOAT64, HAS_PROPERTY) [RPM]
├── Current (FLOAT64, HAS_PROPERTY) [Amps]
├── Temperature (FLOAT64, HAS_PROPERTY) [°C]
└── Mode (INT32, HAS_PROPERTY) [0=Off, 1=Forward, 2=Reverse]
```

**Attributes**:
- `Speed`, `Current`, `Temperature`: minimumSamplingInterval=1000ms, historizing=true
- All: accessLevelFull=1 (READ only, except Mode which is 3=READ_WRITE)

**Use Case**: Conveyor drive motors, spindle motors, fan motors

**Customization**: Add Power, Efficiency, Vibration; extend Mode with more states (ramping, fault)

---

### 3. Valve

**Purpose**: Proportional or on-off flow control valve.

**Structure**:
```
Valve (VARIABLE parent)
├── Position (FLOAT64, HAS_PROPERTY) [0-100%]
├── Pressure (FLOAT64, HAS_PROPERTY) [bar]
└── State (INT32, HAS_PROPERTY) [0=Closed, 1=Open, 2=Error]
```

**Attributes**:
- `Position`: accessLevelFull=3 (READ_WRITE), writeMask=3
- `State`: accessLevelFull=1 (READ only)
- All: minimumSamplingInterval=250ms, historizing=true

**Use Case**: Process control, fluid distribution, pressure relief

**Customization**: Add FluidType, Cv (flow coefficient); support proportional setpoint vs on-off

---

### 4. Tank

**Purpose**: Storage vessel with level and temperature monitoring, including sensor sub-device.

**Structure**:
```
Tank (VARIABLE parent)
├── Level (FLOAT64, HAS_PROPERTY) [%]
├── Temperature (FLOAT64, HAS_PROPERTY) [°C]
└── Sensor (OBJECT, HAS_COMPONENT)
    ├── CalibrationDate (STRING)
    ├── LastMaintenance (STRING)
    └── SerialNumber (STRING)
```

**Attributes**:
- `Level`, `Temperature`: minimumSamplingInterval=2000ms, historizing=true
- `Level`: writeMask=3 (allows calibration offset writes)

**Use Case**: Water tanks, fuel storage, chemical process vessels, mixing tanks

**Customization**: Add Capacity (liters), Material, PressureRating; extend Sensor with LastCalibration timestamp

---

### 5. Conveyor

**Purpose**: Material transport system with speed, load, and runtime monitoring.

**Structure**:
```
Conveyor (VARIABLE parent)
├── Speed (FLOAT64, HAS_PROPERTY) [m/s]
├── MotorCurrent (FLOAT64, HAS_PROPERTY) [A]
├── Count (INT32, HAS_PROPERTY) [pieces]
└── Running (BOOL, HAS_PROPERTY)
```

**Attributes**:
- `Speed`, `MotorCurrent`: minimumSamplingInterval=500ms, historizing=true
- `Count`: writeMask=3 (allows manual reset)
- `Running`: accessLevelFull=3 (READ_WRITE)

**Use Case**: Assembly lines, sorting systems, packaging lines

**Customization**: Add Direction (forward/reverse), Load (%), EmergencyStop flag; track distance or cycles instead of just count

---

## Standards

### 6. PackML State Machine

**Purpose**: IEC 61800-3 state machine for manufacturing equipment (Idle → Running → Error).

**Structure**:
```
PackML (VARIABLE parent)
├── State (INT32, HAS_PROPERTY) [0=Idle, 1=Running, 2=Paused, 3=Stopped, 4=Error, 5=Aborted]
└── Mode (INT32, HAS_PROPERTY) [0=Auto, 1=Manual, 2=Maintenance]
```

**Attributes**:
- `State`: accessLevelFull=1 (READ only), historizing=true
- `Mode`: accessLevelFull=3 (READ_WRITE), historizing=true, writeMask=3

**Use Case**: Any industry-standard equipment reporting operational state

**Governance**: Implements IEC 61800-3 Part 3.2 state transitions

**Customization**: Add timestamps (LastStateChange, TotalRunTime); extend states for line-specific logic

---

### 7. Alarm Handler

**Purpose**: Centralized alarm management and acknowledgment for a device.

**Structure**:
```
Alarms (VARIABLE parent)
├── AlarmList (STRING[100], HAS_PROPERTY) [array of alarm descriptions]
├── Status (INT32, HAS_PROPERTY) [0=OK, 1=Warning, 2=Error, 3=Critical]
└── AckAlarm (METHOD) [acknowledge by index]
```

**Attributes**:
- `AlarmList`: minimumSamplingInterval=1000ms, historizing=true
- `Status`: accessLevelFull=1 (READ only)

**Use Case**: Multi-point alarm aggregation, alarm routing, critical event logging

**Customization**: Add AlarmSeverity array (parallel to AlarmList), timestamp array; add alarm clear/reset methods

---

### 8. Device Identity

**Purpose**: Metadata about the device: manufacturer, model, firmware, network configuration.

**Structure**:
```
Identity (VARIABLE parent)
├── Vendor (STRING, HAS_PROPERTY) ["Siemens", "KUKA", "Bosch Rexroth"]
├── Model (STRING, HAS_PROPERTY)
├── SerialNumber (STRING, HAS_PROPERTY)
├── FirmwareVersion (STRING, HAS_PROPERTY)
└── MacAddress (STRING, HAS_PROPERTY)
```

**Attributes**:
- All: accessLevelFull=1 (READ only), historizing=false

**Use Case**: Device provisioning, inventory, warranty tracking, compatibility verification

**Customization**: Add ManufactureDate, HardwareRevision, InstallationDate; extend with location (Plant/Line/Area)

---

## Sensors

### 9. Temperature Sensor

**Purpose**: Precise temperature measurement with metadata and historical collection.

**Structure**:
```
Temperature (VARIABLE parent)
├── Value (FLOAT64, HAS_PROPERTY) [°C]
├── MinRange (FLOAT64, HAS_PROPERTY) [-50]
├── MaxRange (FLOAT64, HAS_PROPERTY) [150]
├── Unit (STRING, HAS_PROPERTY) ["°C"]
└── EURange (FLOAT64, HAS_PROPERTY) [property with min/max]
```

**Attributes**:
- `Value`: minimumSamplingInterval=500ms, historizing=true, accessLevelFull=1
- `Unit`: historizing=false

**Use Case**: HVAC monitoring, process control, equipment thermal protection

**Customization**: Add Accuracy (±0.5°C), ResponseTime (ms), CorrectionOffset; support multiple ranges

---

### 10. Pressure Sensor

**Purpose**: Pressure measurement for fluid/gas systems.

**Structure**:
```
Pressure (VARIABLE parent)
├── Value (FLOAT64, HAS_PROPERTY) [bar]
├── Unit (STRING, HAS_PROPERTY) ["bar"]
└── Accuracy (FLOAT64, HAS_PROPERTY) [% of range]
```

**Attributes**:
- `Value`: minimumSamplingInterval=500ms, historizing=true
- All: accessLevelFull=1 (READ only)

**Use Case**: Hydraulic systems, pneumatic circuits, vessel monitoring

**Customization**: Add MinAlarm, MaxAlarm thresholds; support psi/Pa/MPa unit switching

---

### 11. Flow Meter

**Purpose**: Volume/mass flow rate measurement.

**Structure**:
```
Flow (VARIABLE parent)
├── FlowRate (FLOAT64, HAS_PROPERTY) [m³/h]
├── TotalVolume (FLOAT64, HAS_PROPERTY) [m³]
└── Unit (STRING, HAS_PROPERTY) ["m³/h"]
```

**Attributes**:
- `FlowRate`: minimumSamplingInterval=500ms, historizing=true
- `TotalVolume`: writeMask=3 (allows reset/calibration), historizing=true

**Use Case**: Water supply, gas distribution, chemical transfer, cooling loops

**Customization**: Add Density (for mass flow), Viscosity; support multiple units (GPM, L/min)

---

### 12. Level Gauge

**Purpose**: Continuous or discrete level measurement for tanks.

**Structure**:
```
Level (VARIABLE parent)
├── Level% (FLOAT64, HAS_PROPERTY) [0-100%]
├── MinAlarm (FLOAT64, HAS_PROPERTY) [10%]
└── MaxAlarm (FLOAT64, HAS_PROPERTY) [90%]
```

**Attributes**:
- `Level%`: minimumSamplingInterval=1000ms, historizing=true
- `MinAlarm`, `MaxAlarm`: accessLevelFull=3 (READ_WRITE), writeMask=3

**Use Case**: Sump monitoring, fuel tank gauging, bin fill detection

**Customization**: Add discrete float switch states; support volumetric display with tank geometry

---

## Advanced Systems

### 13. Multi-Tank System

**Purpose**: Multiple storage vessels with cross-linked monitoring (e.g., transfer between tanks).

**Structure**:
```
MultiTankSystem (VARIABLE parent)
├── Tank1 (VARIABLE, HAS_COMPONENT)
│   ├── Level (FLOAT64, HAS_PROPERTY)
│   └── Temperature (FLOAT64, HAS_PROPERTY)
├── Tank2 (VARIABLE, HAS_COMPONENT)
│   ├── Level (FLOAT64, HAS_PROPERTY)
│   └── Temperature (FLOAT64, HAS_PROPERTY)
└── Tank3 (VARIABLE, HAS_COMPONENT)
    ├── Level (FLOAT64, HAS_PROPERTY)
    └── Temperature (FLOAT64, HAS_PROPERTY)
```

**Attributes**:
- Each Level: minimumSamplingInterval=2000ms, historizing=true
- Each Temperature: minimumSamplingInterval=5000ms, historizing=true

**Use Case**: Multi-stage process (pre-treatment, main, post-treatment), tank farm management

**Customization**: Add transfer lines (Tank1→Tank2 FlowRate); add balancing logic; extend to N tanks

---

### 14. Complex Pump Station

**Purpose**: Multi-pump system with pressure regulation and valve control.

**Structure**:
```
PumpStation (VARIABLE parent)
├── Pump1 (VARIABLE, HAS_COMPONENT)
│   ├── FlowRate (FLOAT64, HAS_PROPERTY)
│   └── Current (FLOAT64, HAS_PROPERTY)
├── Pump2 (VARIABLE, HAS_COMPONENT)
│   ├── FlowRate (FLOAT64, HAS_PROPERTY)
│   └── Current (FLOAT64, HAS_PROPERTY)
├── DischargeValve (VARIABLE, HAS_COMPONENT)
│   ├── Position (FLOAT64, HAS_PROPERTY)
│   └── Pressure (FLOAT64, HAS_PROPERTY)
└── PressureSensor (VARIABLE, HAS_COMPONENT)
    └── Pressure (FLOAT64, HAS_PROPERTY)
```

**Attributes**:
- FlowRate fields: minimumSamplingInterval=500ms, historizing=true
- Pressure fields: minimumSamplingInterval=500ms, historizing=true
- Valve Position: accessLevelFull=3 (READ_WRITE), writeMask=3

**Use Case**: Booster stations, distribution hubs, redundant pump systems

**Customization**: Add inter-pump logic (load sharing); extend to N pumps; add check valves

---

### 15. Manufacturing Cell

**Purpose**: Complete work cell with robot, conveyor, and inspection station.

**Structure**:
```
ManufacturingCell (VARIABLE parent)
├── Robot (VARIABLE, HAS_COMPONENT)
│   ├── Position (INT32, HAS_PROPERTY) [joint angles or tool pose]
│   ├── Speed (FLOAT64, HAS_PROPERTY)
│   ├── Current (FLOAT64, HAS_PROPERTY)
│   └── Running (BOOL, HAS_PROPERTY)
├── Conveyor (VARIABLE, HAS_COMPONENT)
│   ├── Speed (FLOAT64, HAS_PROPERTY)
│   └── Count (INT32, HAS_PROPERTY)
└── InspectionStation (VARIABLE, HAS_COMPONENT)
    ├── CameraReady (BOOL, HAS_PROPERTY)
    ├── PartsOK (INT32, HAS_PROPERTY) [count passed]
    └── PartsNOK (INT32, HAS_PROPERTY) [count failed]
```

**Attributes**:
- Dynamic fields (Position, Speed, Current): minimumSamplingInterval=100ms, historizing=true
- Counters: writeMask=3 (allows batch reset)

**Use Case**: Assembly operations, pick-and-place, quality verification, production tracking

**Customization**: Add cycle time tracking; extend InspectionStation with defect categorization; add inter-device handshakes (robot→conveyor→inspection)

---

## Integration Patterns

### Creating a Custom Device from Multiple Templates

**Scenario**: You need a Pump Station that also monitors ambient Temperature.

1. Add **Complex Pump Station** template
2. Add **Temperature Sensor** template separately
3. Manually link them:
   - Create a parent **Installation** (FOLDER)
   - Move **PumpStation** under Installation
   - Move **Temperature** under Installation
   - Save schema

Result: Hierarchical schema with related sub-systems

---

### Extending a Template

**Scenario**: Your Pump needs Vibration monitoring.

1. Add **Pump** template
2. Manually add new VARIABLE child:
   - Name: `Vibration`
   - Parent: `Pump`
   - DataType: `FLOAT64`
   - Reference type: `HAS_PROPERTY`
3. Set attributes: `minimumSamplingInterval=200ms`, `historizing=true`
4. Save

---

### Round-Trip Workflow

1. **Scan** a real pump device → capture structure + attributes
2. **Compare** with **Pump template** → verify compatibility
3. **Create manual schema** using template as reference
4. **Add** custom nodes for your plant (safety interlocks, maintenance flags)
5. **Reproduce** in simulator → identical to real device structure
6. **Record** simulation data → use for replay/scenarios

---

## Design Principles

### VARIABLE Parents (IS-189)

All templates use **VARIABLE as parent** for Property/Component children:
- **VARIABLE parents**: Represent the main device entity
- **Property children** (HAS_PROPERTY): Metadata (Units, Accuracy, Config)
- **Component children** (HAS_COMPONENT): Sub-devices or major assemblies
- **Reference types**: Properly captured for faithful reproduction

### OPC UA Attributes (IEC 62541)

Each variable respects OPC UA specs:
- **accessLevelFull**: 8-bit mask (1=READ, 2=WRITE, 4=HISTORY_READ, 8=HISTORY_WRITE)
- **minimumSamplingInterval**: Server-recommended sampling rate (ms)
- **writeMask**: Which attributes clients can modify (config params)
- **historizing**: Whether server actively collects history

---

## FAQ

**Q: Can I modify a template after adding it?**
A: Yes. Templates are just starting points. After adding, edit any node freely—change names, add/remove properties, adjust attributes.

**Q: Can I combine multiple templates?**
A: Yes. Add them separately and manually link (create parent FOLDER, move nodes under it, add references).

**Q: Do templates include sample data?**
A: No. Templates define *structure and metadata* only. Use Replay/Synthetic generation to populate values.

**Q: How do I version my custom schema?**
A: Manual schemas are versioned automatically. Each save increments the version. Use ETag for optimistic concurrency.

**Q: Can I export a template I customized?**
A: Yes. Export your schema (project export), extract the JSON, share it. Others can import as a custom template.

---

## See Also

- `backend-specs/01_PROTOCOL_NEUTRAL_MODEL.md` — Schema model details
- `docs/DISCOVERY.md` — Scanning real OPC UA devices
- `docs/REPLAY.md` — Replaying recorded data against simulated schemas
- `docs/SCENARIOS.md` — Building test scenarios with templates

---

**Template Library Version**: 1.0 (IS-189)  
**Last Updated**: 2026-07-24  
**Maintainer**: IoT Simulator team
