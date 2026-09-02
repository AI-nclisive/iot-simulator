## Context

The wizard has a Modbus unit ID field intended for simulated-source settings,
but scan and connection-test requests currently contain only protocol and
endpoint. IS-213 adds an optional `unitId` API property for Modbus real-source
operations.

## Goals / Non-Goals

**Goals:**

- Collect and validate the real-device unit ID independently from simulator
  settings.
- Send the optional property only for Modbus TCP requests.
- Invalidate a previous scan when the unit ID changes.

**Non-Goals:**

- Change the existing simulated Modbus unit ID or address-base configuration.
- Add a Modbus unit concept to OPC UA.
- Implement API or worker behavior owned by IS-213.

## Decisions

- Keep a dedicated `realDeviceUnitId` string in wizard form state, rather than
  repurposing `modbusUnitId`. The two values describe different endpoints and
  avoiding shared state prevents a scan setting from silently changing the
  simulator configuration.
- Validate in the same setup-step validation path as the endpoint. The API
  contract requires a whole number in the inclusive `0..255` range, so invalid
  values prevent a scan rather than yielding an avoidable remote error.
- Include the normalized numeric value in the scan-target cache key. This makes
  a unit-ID edit start a new scan and prevents showing results discovered from a
  different device on the gateway.
- Construct request payloads conditionally by protocol. OPC UA retains its
  current payload and cannot receive an irrelevant Modbus property.

## Risks / Trade-offs

- [Risk] A blank or invalid field can block scan progression. → Mitigation:
  initialize the field to `1` and show a specific validation message.
- [Risk] A stale completed scan can be reused after changing unit ID. →
  Mitigation: include the unit ID in the scan cache key and reset scan state on
  edit.
