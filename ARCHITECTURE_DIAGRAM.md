# Architecture Diagram

Brief visual companion to `ARCHITECTURE.md` (which stays the source of truth).
Status markers (✅ done · 🟡 partial · ⬜ todo) reflect the current snapshot from
`backend-specs/TASKS.md`; the text in `ARCHITECTURE.md` defines the binding
constraints.

## Module map

Dependencies flow downward only; protocol/runtime modules never depend on
UI-facing modules. Solid arrows = runtime/data flow; dotted arrows = compile-time
dependency on the shared foundation.

```mermaid
flowchart TB
    EDGE["Edge Devices / clients under test"]
    UI["Web UI — React/TS<br/>TanStack Query · Zustand · Radix · Tailwind"]

    subgraph BE["Backend — Spring Boot modular monolith"]
        API["api / application layer<br/>REST /api/v1 · OpenAPI · SSE/WS · auth (local/shared)"]
        DOMAIN["domain<br/>projects · schemas · recordings · replay<br/>(scenarios/faults/evidence — todo)"]
        SUP["runtime-supervisor<br/>worker lifecycle · IPC · ports · health"]
    end

    subgraph WORKERS["Protocol workers — out-of-process JVMs (no Spring)"]
        OPCUA["worker-opcua<br/>Eclipse Milo server ✅"]
        MODBUS["worker-modbus<br/>j2mod — skeleton ⬜"]
    end

    subgraph FOUND["Shared foundation"]
        PMODEL["protocol-model<br/>protocol-neutral schema + values"]
        WCONTRACT["worker-contract<br/>ProtocolDataSource .proto v1"]
        PLATFORM["platform<br/>RuntimeController · ObjectStore · Ids"]
    end

    subgraph DATA["Persistence"]
        PERSIST["persistence<br/>jOOQ repos · Flyway V1–V6"]
        PG[("PostgreSQL<br/>entities + value timeline")]
        OBJ[("Object storage<br/>large artifacts")]
    end

    UI -->|"HTTPS REST + SSE"| API
    API --> DOMAIN
    DOMAIN --> SUP
    DOMAIN --> PERSIST
    SUP -->|"gRPC loopback (versioned)"| OPCUA
    SUP -->|"gRPC loopback (versioned)"| MODBUS
    EDGE -->|"OPC UA"| OPCUA
    EDGE -->|"Modbus TCP"| MODBUS

    DOMAIN -.-> PMODEL
    SUP -.-> WCONTRACT
    OPCUA -.-> WCONTRACT
    MODBUS -.-> WCONTRACT
    OPCUA -.-> PMODEL
    PERSIST --> PG
    PLATFORM -.-> OBJ
    PERSIST -.-> PMODEL
```

## Worker lifecycle / IPC

How the supervisor brings a protocol worker up out-of-process and feeds it
values. IPC is loopback-only and versioned; a contract mismatch is refused, not
tolerated.

```mermaid
sequenceDiagram
    actor U as User / Test
    participant API as API
    participant SUP as Supervisor
    participant W as Protocol worker (process)
    participant ED as Edge Device

    U->>API: start data source
    API->>SUP: start(spec)
    SUP->>SUP: allocate port + launch process
    SUP->>W: Hello (contract version)
    alt version matches
        W-->>SUP: Hello ok
    else mismatch
        W-->>SUP: refused
    end
    SUP->>W: Configure(schema, listen port)
    SUP->>W: Start
    ED->>W: connect (OPC UA / Modbus TCP)
    API->>SUP: ApplyValues / replay timeline
    SUP->>W: ApplyValues (stream)
    W-->>ED: serve values
```
