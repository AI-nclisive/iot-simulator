## 1. Types

- [ ] 1.1 Extend `ModbusTypes` with `INT32`/`UINT32`/`FLOAT32` register-pair
      encode/decode (big-endian, MSW-first), default values, and
      `toModbusValue`/`fromModbusValue` helpers mirroring `OpcUaTypes`.

## 2. Server runtime

- [ ] 2.1 `ModbusServerRuntime`: build a j2mod `SimpleProcessImage` from the
      configured schema using the default contiguous layout, sized per
      object type.
- [ ] 2.2 Bind a `ModbusTCPListener` on `bindAddress`/`listenPort`
      (`advertisedHost` option honored the same way as `worker-opcua`).
- [ ] 2.3 `updateValue(nodeId, decoded)` writes into the right
      register/coil array.

## 3. Protocol service (gRPC contract)

- [ ] 3.1 `ModbusWorkerMain` + `WorkerServer`: loopback-only gRPC bind.
- [ ] 3.2 `ModbusProtocolService.hello/health/shutdown` (mirror OPC UA).
- [ ] 3.3 `configure`: parse schema into `ModbusServerRuntime`, default
      layout assignment, track node data types.
- [ ] 3.4 `start`/`stop`.
- [ ] 3.5 `applyValues`/`project(batch)`: decode neutral values, write via
      `ModbusServerRuntime.updateValue`, honor `InjectFault`
      (`BAD_VALUE`/`MISSING_VALUE`/`CONNECTION_DROP`/`DELAY`).
- [ ] 3.6 `testConnection`: j2mod master connect probe against a real
      endpoint.
- [ ] 3.7 `scan`: bounded active probe across coils/discrete
      inputs/holding/input registers; stream `ScanEvent`s
      (progress/NodeBatch/result) like `worker-opcua`; flag adjacent-pair
      32-bit heuristic candidates.
- [ ] 3.8 `capture`: poll loop (configurable interval via `options`),
      diff-on-change streaming of `ValueBatch`, cancellation-safe.
- [ ] 3.9 `clientEvents`/`runtimeEvents`/`injectFault` (mirror OPC UA's
      `ClientEventHub`/`RuntimeEventHub`/fault-state map).

## 4. Tests

- [ ] 4.1 `ModbusWorkerGrpcTest` (contract smoke test, mirrors
      `OpcUaWorkerGrpcTest`).
- [ ] 4.2 `ModbusServerRuntimeTest`/`IT` (configure/start/write/read via a
      real j2mod master against the runtime).
- [ ] 4.3 `ModbusTypesTest` (round-trip encode/decode for every supported
      type, including the register-pair word order).
- [ ] 4.4 `ModbusDiscoveryIT` (scan against a local slave: present vs.
      absent addresses, truncation at `max_nodes`).
- [ ] 4.5 `ModbusCaptureIT` (poll loop observes a value change end-to-end).

## 5. Frontend

- [ ] 5.1 Remove the "hidden until IS-059" gate on the Modbus TCP protocol
      option in `create-data-source-wizard-page.tsx`.
- [ ] 5.2 Confirm existing Modbus-specific wizard fields (`modbusUnitId`,
      `modbusAddressBase`) still validate/submit correctly end-to-end.

## 6. Verification

- [ ] 6.1 `./gradlew build` green (all modules, including `worker-opcua` —
      confirm zero OPC UA regressions).
- [ ] 6.2 Frontend build/tests green.
- [ ] 6.3 Manual smoke: create a Modbus data source from a manual/scanned
      schema, start it, connect a Modbus client, read/write a value.
