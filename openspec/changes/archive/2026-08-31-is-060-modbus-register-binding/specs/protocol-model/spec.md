## MODIFIED Requirements

### Requirement: Protocol projection
Each worker SHALL map the neutral schema onto its native address model
deterministically, using default rules where no explicit binding is set. A
schema node MAY carry an explicit, persisted protocol-specific binding (for
Modbus TCP: a register/coil kind and address) that overrides the default
rule for that node; the worker SHALL honor an explicit binding verbatim and
ensure every node without one still receives a non-colliding default
address.

#### Scenario: OPC UA projection
- **WHEN** an OPC UA worker is configured with a neutral schema
- **THEN** folders become `FolderType` objects, variables become
  `BaseDataVariableType` nodes, and each `nodeId` maps to an OPC UA `NodeId`
  in a worker-allocated namespace

#### Scenario: Modbus default register layout
- **WHEN** a Modbus TCP schema variable has no explicit `protocolBindings.modbus`
- **THEN** the worker assigns a contiguous register address in schema order
  and surfaces the assignment for user review

#### Scenario: Explicit Modbus register binding is honored
- **WHEN** a Modbus TCP schema variable has an explicit register/coil kind
  and address set
- **THEN** the worker projects that variable at exactly that address instead
  of computing a default one

#### Scenario: Default assignment skips an explicitly reserved address
- **WHEN** one variable has an explicit binding at a given address and
  another variable of the same register/coil kind has no explicit binding
- **THEN** the second variable's default-assigned address does not collide
  with the first variable's explicit address
