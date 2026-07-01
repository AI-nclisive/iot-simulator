# IS-125 — Supervisor mode fails to start: ambiguous SourceCapturer/SourceScanner beans (design)

**Task:** IS-125 [BE] · P2 · bug fix
**Issue:** [#341](https://github.com/AI-nclisive/iot-simulator/issues/341)
**Branch:** `fix/IS-125-supervisor-bean-ambiguity`
**Blocks:** IS-123 (`/run-local` supervisor mode). Found during IS-123 verification.

## Problem

The Spring context **fails to start in `IOTSIM_RUNTIME_MODE=supervisor`**:
```
No qualifying bean of type '…SourceCapturer' available: expected single matching bean but found 2:
runtimeController, sourceCapturer  →  APPLICATION FAILED TO START
```
`Supervisor implements RuntimeController, SourceScanner, SourceCapturer`. In
`RuntimeConfig`, the `runtimeController` bean (= `Supervisor` in supervisor mode)
therefore also qualifies as `SourceCapturer` and `SourceScanner`, colliding with the
dedicated `sourceCapturer`/`sourceScanner` beans → two candidates of each type →
ambiguous injection into `RecordingService` (`SourceCapturer`) and the scan path
(`SourceScanner`). In `memory` mode `InMemoryRuntimeController` implements neither, so
there is a single bean and it boots. Supervisor mode has never booted (tests, CI, and
`ApplicationSmokeIT` all run the `memory` default), so this went unnoticed.

## Fix

Mark the **`runtimeController` bean `@Primary`** in `app/.../config/RuntimeConfig.java`.
In supervisor mode the single `Supervisor` instance is registered under three bean
names (`runtimeController`, `sourceScanner`, `sourceCapturer`) and satisfies all three
interfaces — so `@Primary` on the two helper beans would make *two* primaries for
`SourceScanner`/`SourceCapturer` (they too are the Supervisor) and still fail. Marking
only `runtimeController` `@Primary` gives exactly one primary among the candidates for
each of `RuntimeController`/`SourceScanner`/`SourceCapturer`, so every injection
resolves to the one true Supervisor. No consumer changes; memory mode is unaffected
(there `runtimeController` = `InMemoryRuntimeController`, not a `SourceScanner`/
`SourceCapturer`, so those types have a single candidate anyway and `@Primary` is a
harmless no-op).

## Test (closes the coverage gap)

Add `app/src/test/.../SupervisorModeContextIT.java` — `@SpringBootTest` +
Testcontainers Postgres (mirroring `ApplicationSmokeIT`) but with
`iotsim.runtime.mode=supervisor` set via `@DynamicPropertySource`. One test asserts
the context boots (e.g. `/actuator/health` → 200). This is the first test that boots
supervisor mode; it fails before the fix (ambiguous beans) and passes after. The
`Supervisor` bean is built without spawning any worker (workers spawn only on source
`start`), so the test needs no worker binary.

## Out of scope

Spawning a real worker in the test; the `/run-local` tooling (IS-123, builds on this);
graceful worker shutdown (IS-090).

## Definition of done

`./gradlew build` green (incl. the new supervisor-mode IT on real Postgres); IS-125
catalog line added + `[x]` in `backend-specs/TASKS.md` in the PR; board → In review via
`/open-pr`.
