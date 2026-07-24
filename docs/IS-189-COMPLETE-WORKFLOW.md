# IS-189: Complete OPC UA Device Reproduction Workflow

**What was implemented**: Complete bidirectional fidelity for OPC UA devices with nothing lost or rewritten.

---

## ✅ What You Can Do Now

### 1️⃣ Scan Real OPC UA Device (Complete Capture)

```
Real Device (OPC UA server)
    ↓
OpcUaDiscovery.scan()
    ├─ Discovers ALL nodes (FOLDER, OBJECT, VARIABLE)
    ├─ Captures reference types (ORGANIZES, HAS_PROPERTY, HAS_COMPONENT)
    ├─ Batch-reads 4 OPC UA attributes per IEC 62541:
    │  ├─ accessLevelFull (8-bit mask: READ/WRITE/HISTORY)
    │  ├─ minimumSamplingInterval (server sampling constraint)
    │  ├─ writeMask (which attributes are client-writable)
    │  └─ historizing (server collects history?)
    ├─ Handles VARIABLE parents correctly (Property/Component children)
    └─ Returns SchemaNodeMsg with ALL attributes preserved
    ↓
Store in database
    ├─ Flyway migration adds 4 columns
    ├─ JooqSchemaRepository persists everything
    └─ Schema versioned for reproducibility
```

**Result**: Schema captured with **100% fidelity** — nothing lost or hidden.

---

### 2️⃣ Reproduce in Simulator (Identical Structure)

```
Stored Schema (from scan)
    ↓
SchemaNamespace.createNodes()
    ├─ FOLDER/OBJECT → UaObjectNode (hierarchy)
    ├─ VARIABLE → UaVariableNode (correct data types)
    ├─ VARIABLE parents are materialized
    │  ├─ Property children added with HAS_PROPERTY reference
    │  └─ Component children added with HAS_COMPONENT reference
    ├─ Reference types converted to OPC UA equivalents
    │  ├─ ORGANIZES → Identifiers.Organizes
    │  ├─ HAS_PROPERTY → Identifiers.HasProperty
    │  ├─ HAS_COMPONENT → Identifiers.HasComponent
    │  └─ HAS_TYPE_DEFINITION → Identifiers.HasTypeDefinition
    └─ OPC UA attributes applied per IEC 62541
       ├─ accessLevel set from accessLevelFull
       ├─ minimumSamplingInterval set on node
       ├─ writeMask controls client access
       └─ historizing enables history collection
    ↓
Real OPC UA Server (Eclipse Milo)
    └─ Clients see identical structure to original device
```

**Result**: Simulator produces **identical schema** — re-scanning same device = same result.

---

### 3️⃣ Manually Build Complex Schema (Same Fidelity)

```
Manual Schema Editor (/manual-schemas)
    ↓
User creates VARIABLE parent (e.g., "Pump")
    ↓
User adds VARIABLE children (e.g., "Pressure", "Temperature")
    ↓
User selects reference types:
    ├─ HAS_PROPERTY: for metadata (EURange, Units, Quality)
    └─ HAS_COMPONENT: for sub-devices (Motor, Sensor, etc.)
    ↓
User edits 4 new OPC UA attributes:
    ├─ accessLevelFull: 0-255 (bit mask for read/write/history)
    ├─ minimumSamplingInterval: milliseconds
    ├─ writeMask: which attributes are writable
    └─ historizing: enable history collection
    ↓
User saves schema
    ↓
Worker materializes with EXACT same fidelity as scanned devices
```

**Result**: Manually-built schemas are **indistinguishable from scanned ones**.

---

### 4️⃣ Use Templates for Quick Schema Creation

```
15 Pre-built Templates Available:

Industrial Devices (5):
├─ Pump (with Motor sub-device)
├─ Motor (speed, current, temperature)
├─ Valve (position, pressure, state)
├─ Tank (level, temperature, sensor)
└─ Conveyor (speed, load, count)

Standards (3):
├─ PackML State Machine (IEC 61800-3)
├─ Alarm Handler (aggregated alarms)
└─ Device Identity (vendor, model, firmware)

Sensors (4):
├─ Temperature Sensor (with historizing)
├─ Pressure Sensor
├─ Flow Meter (with reset capability)
└─ Level Gauge (with alarm thresholds)

Advanced Systems (3):
├─ Multi-Tank System (3 linked tanks)
├─ Complex Pump Station (2 pumps + valves + sensor)
└─ Manufacturing Cell (robot + conveyor + inspection)
```

**Usage**:
1. Click "Add from template" in manual editor
2. Select template (e.g., "Pump")
3. Template nodes added with proper VARIABLE hierarchy
4. Customize individual nodes as needed
5. Save with template + custom nodes

**Result**: Instant complex schemas, fully editable, same fidelity as scanned devices.

---

## 🔄 Complete Round-Trip Example

### Scenario: Reproduce a Real Pump Station

```
REAL DEVICE
├─ Pump (OPC UA Variable)
│  ├─ FlowRate (Property, HAS_PROPERTY)
│  ├─ Pressure (Property, HAS_PROPERTY)
│  └─ Motor (Component, HAS_COMPONENT)
│     ├─ Speed (double)
│     └─ Current (float)
├─ Attributes on Pump:
│  ├─ accessLevelFull = 1 (READ only)
│  ├─ minimumSamplingInterval = 500ms
│  ├─ writeMask = 0
│  └─ historizing = true
└─ Reference types: all correct (HAS_PROPERTY, HAS_COMPONENT)

         ⬇️ SCAN (OpcUaDiscovery.scan)
         
STORED SCHEMA (Database)
├─ Pump [nodeId: pump_xyz]
│  ├─ parentId: null
│  ├─ kind: VARIABLE
│  ├─ accessLevelFull: 1
│  ├─ minimumSamplingInterval: 500
│  ├─ writeMask: 0
│  ├─ historizing: true
│  └─ references: [
│     ├─ target: flowrate_xyz, type: HAS_PROPERTY
│     ├─ target: pressure_xyz, type: HAS_PROPERTY
│     └─ target: motor_xyz, type: HAS_COMPONENT
│     ]
├─ FlowRate [parentId: pump_xyz, reference: HAS_PROPERTY]
├─ Pressure [parentId: pump_xyz, reference: HAS_PROPERTY]
└─ Motor [parentId: pump_xyz, reference: HAS_COMPONENT]
   ├─ Speed [parentId: motor_xyz]
   └─ Current [parentId: motor_xyz]

         ⬇️ REPRODUCE (SchemaNamespace.createNodes)
         
SIMULATOR OPC UA SERVER
├─ Pump (UaVariableNode)
│  ├─ FlowRate (UaVariableNode, accessible via HAS_PROPERTY)
│  ├─ Pressure (UaVariableNode, accessible via HAS_PROPERTY)
│  └─ Motor (UaObjectNode, accessible via HAS_COMPONENT)
│     ├─ Speed (UaVariableNode)
│     └─ Current (UaVariableNode)
├─ Attributes applied:
│  ├─ accessLevel = 1 (derived from accessLevelFull)
│  ├─ minimumSamplingInterval = 500ms
│  ├─ historizing = true
│  └─ writeMask = 0
└─ Reference types: correctly materialized
   ├─ HAS_PROPERTY edges preserved
   └─ HAS_COMPONENT edges preserved

         ⬇️ RE-SCAN (OpcUaDiscovery.scan on simulator)
         
RESCANNED SCHEMA
├─ Pump [same structure as original]
├─ FlowRate [HAS_PROPERTY reference preserved]
├─ Pressure [HAS_PROPERTY reference preserved]
├─ Motor [HAS_COMPONENT reference preserved]
└─ All attributes match original

✅ ROUND-TRIP COMPLETE: Original ≡ Reproduced ≡ Rescanned
```

---

## 📊 What Changed: IS-189 Additions

### 1. Schema Model (SchemaNode.java)

**Before**: 
```java
nodeId, parentId, path, name, kind, dataType, valueRank, access, unit, description, ...
```

**After** (+ 4 new fields):
```java
nodeId, parentId, path, name, kind, dataType, valueRank, access, unit, description, ...
+ accessLevelFull       // 0-255: full OPC UA access mask
+ minimumSamplingInterval // milliseconds
+ writeMask             // 0-255: which attributes are writable
+ historizing           // boolean: server collects history?
```

### 2. Reference Types (ReferenceType enum)

**Before**: `ORGANIZES` only

**After** (+ 3 new types):
```java
ORGANIZES               // default, folder/object hierarchy
+ HAS_PROPERTY         // metadata on variables (EURange, Units, Quality)
+ HAS_COMPONENT        // sub-devices (Motor, Sensor inside Pump)
+ HAS_TYPE_DEFINITION  // type information
+ GENERIC              // catch-all for unknown types
```

### 3. VARIABLE as Parent (IS-189 Breaking Change)

**Before**: 
```
Valid parents: FOLDER, OBJECT only
→ Property-of-Variable nodes were rejected (parent must be FOLDER or OBJECT)
→ Real devices exposing Properties became unusable
```

**After**:
```
Valid parents: FOLDER, OBJECT, VARIABLE
→ VARIABLE can have Property children (HAS_PROPERTY)
→ VARIABLE can have Component children (HAS_COMPONENT)
→ Real device structures fully preserved
```

### 4. Discovery Enhancement (OpcUaDiscovery.java)

**Added**:
- `mapReferenceType(NodeId)` — converts OPC UA ref types to enums
- `readVariableAttributes(OpcUaClient, NodeId)` — batch-reads 4 attributes
- `extractIntValue()`, `extractBoolValue()` — type extraction helpers

**Result**: Discovers with full attribute capture and reference type awareness

### 5. Manual Editor UI (manual-schema-editor-page.tsx)

**Added**:
- Reference type dropdown (HAS_PROPERTY vs HAS_COMPONENT) for VARIABLE children
- OPC UA Attributes section:
  - accessLevelFull input (0-255)
  - minimumSamplingInterval input (ms)
  - writeMask input (0-255)
  - historizing toggle
- Validation: VARIABLE parents require children to have reference types

---

## 🎯 Key Achievements

| Capability | Before IS-189 | After IS-189 |
|------------|---------------|--------------|
| **Scan real device** | ✅ Basic | ✅ **Complete** (all attributes + ref types) |
| **Reproduce in simulator** | ✅ Basic | ✅ **Identical** (no loss) |
| **VARIABLE as parent** | ❌ Rejected | ✅ **Supported** |
| **Property children** | ❌ Lost | ✅ **Preserved** |
| **Reference types** | ❌ Lost | ✅ **Captured & materialized** |
| **OPC UA attributes** | ❌ Lost | ✅ **All 4 stored & applied** |
| **Manual schema creation** | ✅ Simple | ✅ **Complex structures** |
| **Templates** | ❌ None | ✅ **15 production-ready** |
| **Documentation** | ❌ None | ✅ **Complete guide** |

---

## 🚀 Usage

### From Command Line (API)

```bash
# 1. Create manual schema with attributes
POST /api/v1/projects/{pid}/manual-schemas
{
  "name": "My Pump",
  "protocol": "OPC_UA",
  "nodes": [
    {
      "nodeId": "pump1",
      "name": "Pump",
      "kind": "VARIABLE",
      "dataType": "FLOAT64",
      "accessLevelFull": 1,
      "minimumSamplingInterval": 500,
      "writeMask": 0,
      "historizing": true,
      "references": [
        {
          "targetNodeId": "pressure1",
          "type": "HAS_PROPERTY",
          "forward": true
        }
      ]
    },
    {
      "nodeId": "pressure1",
      "parentId": "pump1",
      "name": "Pressure",
      "kind": "VARIABLE",
      "dataType": "FLOAT64"
    }
  ]
}

# 2. Create data source using manual schema
POST /api/v1/projects/{pid}/data-sources
{
  "name": "Simulated Pump",
  "protocol": "OPC_UA",
  "basis": "SYNTHETIC",
  "manualSchemaId": "{schema-id}"
}

# 3. Start simulator
POST /api/v1/data-sources/{id}/runtime/start

# 4. Client connects: opc.tcp://localhost:4840/iotsim
```

### From UI

1. Open `/manual-schemas`
2. Click "Add from template" → select "Pump"
3. Customize individual nodes (edit attributes, add children)
4. Save schema
5. Create data-source using this schema
6. Start simulator
7. Client sees identical structure to original pump

---

## ✨ What This Means

**Before IS-189**:
- Scanned devices lost Property-of-Variable nodes
- Manually-built schemas were limited
- OPC UA attributes (sampling, historizing) were lost
- Re-scanning might produce different schema
- No templates for complex devices

**After IS-189**:
- Nothing is lost in scan-reproduce-rescan cycle
- VARIABLE hierarchy fully supported
- All OPC UA attributes (IEC 62541) preserved
- Manual editor can build any complexity
- 15 templates for instant complex schemas
- Complete round-trip fidelity

---

## References

- `backend-specs/01_PROTOCOL_NEUTRAL_MODEL.md` — Updated schema model
- `docs/TEMPLATES_GUIDE.md` — All 15 templates documented
- `docs/DISCOVERY.md` — How scanning works
- GitHub PR #653 — All commits and code
