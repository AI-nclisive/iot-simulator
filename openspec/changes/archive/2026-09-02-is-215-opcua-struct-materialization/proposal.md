## Why

An OPC UA schema collected from a real server can contain a custom structured
data type whose declaration could not be captured. Starting a replay or
live-served source currently lets that one node abort the whole worker Start
RPC with an unhelpful internal error.

## What Changes

- Omit OPC UA variables whose custom data-type declaration is unavailable from
  simulated address-space materialization.
- Emit a runtime warning identifying each omitted variable and its unavailable
  native data type while continuing to start the other configured nodes.
- Keep Start failures within the worker RPC boundary so a materialization
  failure cannot escape as an uncaught gRPC application error.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `worker-contract`: An OPC UA worker tolerates one unsupported native
  structured variable during address-space materialization and reports it to
  the runtime event stream instead of failing the whole start.

## Impact

Affected code: the OPC UA worker's schema namespace, server runtime, and Start
RPC tests. The existing `RuntimeEvents` stream carries the warning; no contract
or dependency changes are required.
