## Context

The copied scan schema can contain a native `dataTypeNodeId` instead of a
neutral `dataType`, while a synthetic configuration always selects a neutral
executable type.

## Goals / Non-Goals

**Goals:** materialize selected types only for configured synthetic variables.

**Non-Goals:** infer arbitrary native types or change non-configured nodes.

## Decisions

Build a copied variable with its `dataType` from the configuration and clear
the schema-native type binding, retaining the original declared type metadata.
This meets the schema invariant that a variable has one executable type.

## Risks / Trade-offs

- [Unselected native nodes remain non-executable] → they are not driven by the
  synthetic config and retain their scanned representation.
