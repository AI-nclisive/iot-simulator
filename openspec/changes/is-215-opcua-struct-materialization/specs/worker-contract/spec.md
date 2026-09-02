## ADDED Requirements

### Requirement: OPC UA simulation isolates incomplete native declarations
When an OPC UA worker starts a simulated address space, a variable that refers
to a non-standard native data type whose declaration is unavailable SHALL be
excluded from that address space. The worker SHALL continue starting all other
materializable configured nodes and emit a runtime warning that identifies the
excluded node and unavailable native type. It SHALL not silently coerce the
variable to a different data type or let this condition escape the Start RPC as
an uncaught application error.

#### Scenario: One opaque native variable is excluded
- **WHEN** an OPC UA schema contains a scalar variable and a variable with a
  non-standard data type that has no supplied declaration
- **THEN** Start succeeds, the scalar variable is served, the opaque variable
  is absent, and a runtime warning identifies the opaque variable and type
