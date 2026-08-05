## ADDED Requirements

### Requirement: Schema transport field set
The schema message `Configure` sends to a worker carries the original
folder/variable-era attribute set only. It does NOT yet carry
`arrayDimensions`, `typeDefinition`, or a `DATA_TYPE` node's `members`, so the
extended address-space attributes in the `protocol-model` capability cannot
reach a worker over the contract today. A schema relying on them SHALL be
refused or reported as unsupported at configure time rather than sent with
those attributes silently stripped.

#### Scenario: Extended attributes are not silently dropped on the wire
- **WHEN** a schema whose nodes carry extended address-space attributes is
  sent to a worker via `Configure`
- **THEN** the caller learns those attributes are unsupported, instead of the
  worker serving a silently degraded address space
