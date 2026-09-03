## ADDED Requirements

### Requirement: Modbus bindings can configure numeric register encoding

A Modbus register binding MAY specify a byte order (`BIG_ENDIAN` or
`LITTLE_ENDIAN`), a multi-register word order (`MSW_FIRST` or `LSW_FIRST`),
and a finite non-zero numeric scale. Missing byte order, word order, and scale
SHALL respectively mean `BIG_ENDIAN`, `MSW_FIRST`, and `1`, preserving the
existing binding semantics. These settings apply only to numeric register
variables, not coils or discrete inputs.

#### Scenario: Existing binding retains its previous layout

- **WHEN** a Modbus register binding has no encoding or scale metadata
- **THEN** it uses big-endian bytes, most-significant-word-first ordering, and
  scale `1`

#### Scenario: A scaled little-endian float is projected

- **WHEN** a `FLOAT32` holding-register binding specifies `LITTLE_ENDIAN`,
  `LSW_FIRST`, and scale `0.1`
- **THEN** the worker maps the neutral engineering value to the corresponding
  raw register representation and reverses that mapping on capture
