## ADDED Requirements

### Requirement: Schema-node Modbus encoding metadata is persisted

`schema_nodes` SHALL persist nullable `modbus_byte_order`,
`modbus_word_order`, and `modbus_scale` columns with the explicit Modbus
binding. Byte order values SHALL be constrained to `BIG_ENDIAN` or
`LITTLE_ENDIAN`; word order values SHALL be constrained to `MSW_FIRST` or
`LSW_FIRST`; a present scale SHALL be finite and non-zero at application
validation time.

#### Scenario: Existing binding remains readable

- **WHEN** a row written before the encoding metadata migration is read
- **THEN** its null metadata is interpreted as the established default layout
  and scale
