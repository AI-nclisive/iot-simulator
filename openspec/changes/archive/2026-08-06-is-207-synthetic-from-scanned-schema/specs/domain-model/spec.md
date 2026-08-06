## ADDED Requirements

### Requirement: Synthetic sources materialize compatible scanned variables
When creating a synthetic source from a scanned schema, the system SHALL use
the synthetic configuration's selected executable type for each configured
variable whose scanned representation has only a native type binding, while
preserving the original declared source type metadata.

#### Scenario: Scanned numeric variable becomes synthetic
- **WHEN** a scanned variable has a native type binding and the synthetic
  configuration selects `FLOAT64`
- **THEN** synthetic source creation succeeds and its variable is executable as
  `FLOAT64`
