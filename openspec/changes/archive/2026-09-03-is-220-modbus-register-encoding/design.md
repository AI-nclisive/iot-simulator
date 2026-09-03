## Context

The current Modbus worker maps 16-bit registers directly and maps 32-bit
values as big-endian, most-significant-word first. Binding metadata already
owns the Modbus register kind and address, so encoding metadata belongs with
that binding rather than with neutral values.

## Decisions

### Persist defaults explicitly by absence

`modbusByteOrder`, `modbusWordOrder`, and `modbusScale` are nullable. Absent
order fields mean big-endian bytes and MSW-first words; absent scale means
`1`. This preserves every existing schema and payload without migration of
existing rows.

### Use engineering-value scale

The neutral value is the engineering value. Serving encodes
`raw = engineering / scale`; capture decodes `engineering = raw * scale`.
Scale must be finite and non-zero. The transform applies only to register
numeric types, never boolean coils.

### Centralize register conversion

`ModbusTypes` owns a pure encode/decode conversion with configurable byte and
word order. Both server writes and capture reads invoke it, preventing their
layouts from drifting.

### Carry metadata on the existing schema message

New additive `SchemaNodeMsg` fields transport the binding configuration across
the worker boundary. Existing workers and callers retain protobuf defaults.
