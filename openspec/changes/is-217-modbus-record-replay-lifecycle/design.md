## Context

The Modbus worker already has independent coverage for capture polling and
register projection, but no test connects the two lifecycles. The worker test
module can host a local j2mod slave and master using existing dependencies.

## Goals / Non-Goals

**Goals:**

- Exercise a complete loopback Modbus TCP capture and replay lifecycle.
- Preserve captured value order and prove the replay endpoint exposes the
  final captured value.
- Keep the test independent of a physical Modbus device and external services.

**Non-Goals:**

- Testing recording persistence, REST controllers, or worker process spawning.
- Changing capture polling or replay production logic.

## Decisions

### Use two in-process Modbus protocol services over loopback TCP

One configured service provides the capture source and a second configured
service provides the replay endpoint. `ModbusCapture` records initial and
changed values from the source; the captured batches are then sent through the
replay service's existing ApplyValues path and verified with a real Modbus
master.

This covers the production Modbus TCP protocol boundary without needing a
physical device. A mock master or direct register mutation was rejected because
it would skip the actual capture and projection paths.

### Make progression deterministic through explicit values and bounded waits

The source begins with a fixed register value, receives one known update after
capture starts, and the test waits only for those two values. Replay applies
the ordered captured values and asserts the fixed final value through the
network client.

## Risks / Trade-offs

- [Polling is asynchronous] → Use bounded condition waits and close every
  capture, client, and service in teardown.
- [Loopback ports can conflict under parallel tests] → Reserve unique
  non-privileged ports for source and replay endpoints per test invocation.

## Migration Plan

The change is test-only. It deploys with the normal test suite and can be
rolled back by removing the integration test.
