## 1. Types

- [x] 1.1 Extend `ModbusTypes` with `INT32`/`UINT32`/`FLOAT32` register-pair
      encode/decode (big-endian, MSW-first), default values, and
      `toModbusValue`/`fromModbusValue` helpers mirroring `OpcUaTypes`.

## 2. Server runtime

- [x] 2.1 `ModbusServerRuntime`: build a j2mod `SimpleProcessImage` from the
      configured schema using the default contiguous layout, sized per
      object type.
- [x] 2.2 Bind a j2mod `ModbusSlave` on `bindAddress`/`listenPort` (via
      `ModbusSlaveFactory`; no `advertisedHost` equivalent for Modbus — see
      design.md decision 5).
- [x] 2.3 `updateValue(nodeId, decoded)` writes into the right
      register/coil array.

## 3. Protocol service (gRPC contract)

- [x] 3.1 `ModbusWorkerMain` + `WorkerServer`: loopback-only gRPC bind.
- [x] 3.2 `ModbusProtocolService.hello/health/shutdown` (mirror OPC UA).
- [x] 3.3 `configure`: parse schema into `ModbusServerRuntime`, default
      layout assignment, track node data types.
- [x] 3.4 `start`/`stop`.
- [x] 3.5 `applyValues`/`project(batch)`: decode neutral values, write via
      `ModbusServerRuntime.updateValue`, honor `InjectFault`
      (`BAD_VALUE`/`MISSING_VALUE`/`CONNECTION_DROP`/`DELAY`).
- [x] 3.6 `testConnection`: j2mod master connect probe against a real
      endpoint.
- [x] 3.7 `scan`: bounded active probe across coils/discrete
      inputs/holding/input registers; stream `ScanEvent`s
      (progress/NodeBatch/result) like `worker-opcua`; flag adjacent-pair
      32-bit heuristic candidates.
- [x] 3.8 `capture`: poll loop (fixed 500ms interval — `CaptureRequest` has
      no options map to make it configurable, see design.md decision 4),
      diff-on-change streaming of `ValueBatch`, cancellation-safe.
- [x] 3.9 `clientEvents`/`runtimeEvents`/`injectFault` (mirror OPC UA's
      `ClientEventHub`/`RuntimeEventHub`/fault-state map). Note:
      `ClientEvents` has no emitters wired yet — j2mod does not expose a
      connect/disconnect hook as directly as Milo's session listeners; the
      stream registration works, but no real Modbus client connect/
      disconnect event is published. Left as a follow-up, not required for
      write/scan/capture/replay to work.

## 4. Tests

- [x] 4.1 `ModbusWorkerGrpcTest` (contract smoke test, mirrors
      `OpcUaWorkerGrpcTest`).
- [x] 4.2 `ModbusServerRuntimeIT` (configure/start/write/read via a real
      j2mod master against the runtime).
- [x] 4.3 `ModbusTypesTest` (round-trip encode/decode for every supported
      type, including the register-pair word order).
- [x] 4.4 `ModbusDiscoveryIT` (scan against a local slave: present vs.
      absent addresses, bounded probing on an empty object type).
- [x] 4.5 `ModbusCaptureIT` (poll loop observes a value change end-to-end).

## 5. Frontend

- [x] 5.1 Remove the "hidden until IS-059" gate on the Modbus TCP protocol
      option in `create-data-source-wizard-page.tsx` (also removed the
      separate UI-463 gate in the data-sources list page's protocol filter).
- [x] 5.2 Confirm existing Modbus-specific wizard fields (`modbusUnitId`,
      `modbusAddressBase`) still validate/submit correctly end-to-end
      (existing wizard tests green after unhiding).

## 6. Verification

- [x] 6.1 `./gradlew build` green (all modules, including `worker-opcua` —
      confirmed zero OPC UA regressions: its test/check/build tasks stayed
      UP-TO-DATE across the change).
- [x] 6.2 Frontend build/tests green (800 tests, typecheck clean).
- [x] 6.3 Manual smoke: covered by `ModbusServerRuntimeIT`/`ModbusDiscoveryIT`/
      `ModbusCaptureIT`/`ModbusWorkerGrpcTest`, which create a real j2mod
      slave from a schema, start it, and read/write/scan/poll it with a real
      j2mod master end-to-end. No interactive third-party Modbus client tool
      was used in addition to this.
