## Context

IS-206 batches initial `Read` requests but still creates every monitored item
in one `CreateMonitoredItems` operation. The public demo source has 883
variables; that request can stall or fail before any initial read is sent.

## Goals / Non-Goals

**Goals:**

- Establish capture for large real schemas in bounded OPC UA operations.
- Deliver readable initial values without waiting for later batches.
- Preserve subscriptions from successful batches if another batch fails.

**Non-Goals:**

- Change the Capture RPC, recording persistence, or value encoding.
- Guarantee all configured nodes are accepted by a remote server.

## Decisions

- Use the existing 100-node read bound for monitored-item batches.
- Create one OPC UA subscription per monitored-item batch because the Milo
  helper creates all items attached to one subscription in a single operation.
- Read and emit the initial values immediately after each successful
  subscription batch. Continue with remaining batches after a batch failure;
  only fail capture when none can be established.

## Risks / Trade-offs

- Multiple subscriptions add server-side objects, but keep each request within
  the common server limits and are all deleted when capture stops.
- A later failed batch is visible as missing variables, while successful
  variables remain recordable as required by the worker contract.

## Migration Plan

The change is backward-compatible and has no persisted-state migration. New
recordings use bounded subscriptions after deployment.
