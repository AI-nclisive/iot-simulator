# IS-061 — Resource governance (concurrent-source caps, backpressure) (design)

**Task:** IS-061 [BE] · Wave D — Creation/reuse breadth & synthetic generation · P1
**Issue:** [#82](https://github.com/AI-nclisive/iot-simulator/issues/82)
**Owning spec:** `backend-specs/02_WORKER_CONTRACT_AND_IPC.md` §5 (Port allocation & resource governance); supports `SPEC.md` → *Run Multiple Data Sources Concurrently*
**Branch:** `feat/IS-061-resource-governance`

## Problem

The supervisor launches one out-of-process worker (a separate JVM) per running
data source and tracks them in a `running` map. Today there is **no limit**: an
operator (or a buggy client) can keep calling `start()` and the supervisor will
keep spawning JVMs until the host is exhausted — degrading every other running
source and the simulator itself.

`02 §5` already specifies the fix: *"Resource governance: supervisor enforces
caps on concurrent workers and may refuse new starts under pressure (supports
SPEC 'Run Multiple Data Sources Concurrently')."* This is admission control —
the supervisor refuses a **new** start once a configured ceiling is reached,
rather than overcommitting the host. Governance lives **only** in the supervisor
(`02 §7`).

## Scope

In scope:

- A **global** cap on the number of concurrent **long-running source workers**
  (the `running` map / `start()` path).
- **Admission control**: refuse a *new* `start()` once the cap is reached.
- A dedicated exception surfaced as **HTTP 503 Service Unavailable**.
- Configurable cap (default **50**) with an explicit **unlimited** opt-out.

Out of scope (explicit, conscious boundaries — not oversights):

- **Per-project caps.** The supervisor only sees `dataSourceId`; it has no
  project concept. A per-project cap would require threading `projectId` through
  the `RuntimeController.start` port, the domain, and all callers/tests. The
  capability ("run multiple sources concurrently") is satisfied by a global cap.
- **Transient scan/capture workers.** `scan()` and `capture()` launch
  short-lived client-mode workers **directly** via `launcher.launch(...)`; they
  never enter the `running` map and are not source runs. They are not counted
  against the cap.
- **Value-stream flow control.** Throttling the `ApplyValues` push is a separate
  concern from "concurrent-source caps" and is not part of `02 §5`'s
  "refuse new starts under pressure".

## Approach

### 1. `ResourceGovernancePolicy` (record, `runtime-supervisor`)

Mirrors the existing `RestartPolicy` / `HealthPolicy` pattern.

- Field: `int maxConcurrentWorkers`.
- `DEFAULT` = `maxConcurrentWorkers = 50` (each worker is a separate JVM, so the
  default protects the host while never tripping in normal multi-source use).
- Sentinel: `maxConcurrentWorkers <= 0` ⇒ **unlimited** — an explicit opt-out
  that preserves today's unbounded behavior.
- Validation: the record canonical constructor accepts any int; `<= 0` is
  normalized to the "unlimited" meaning (no exception).

### 2. Admission gate (inside `Supervisor`)

A `java.util.concurrent.Semaphore` sized to `maxConcurrentWorkers` provides a
strict, race-free global cap. When the policy is **unlimited**, no semaphore is
created and the gate is a no-op (today's behavior).

Invariant: **a permit corresponds to a worker occupying a host slot** — one that
is `RUNNING`, `STARTING`, or recovering (in backoff). It is **not** tied to the
mere presence of a map entry, because an `ERROR` worker (restart budget
exhausted) lingers in the `running` map with no live process until it is replaced.

Permit lifecycle:

- **`start()` admitting a genuinely new worker** (map entry absent, or replacing
  an `ERROR`/terminal entry per the existing replace rule): `tryAcquire()` a
  permit.
  - Fail ⇒ throw `RuntimeCapacityException` (nothing added to the map).
  - Success ⇒ launch. If `launchAndStart()` throws, `release()` the permit
    (rollback) so a failed launch never leaks a slot.
- **Idempotent re-start of an already-active source** (`existing.isActive()`):
  returns the existing worker, **takes no permit**.
- **Restart-with-backoff** reuses the permit already held by the recovering
  worker — recovery is **never re-admitted**, so a worker that is restarting
  cannot be locked out by a full cap. The permit is held across the backoff
  window (the slot stays reserved for recovery).
- **Release exactly once** on the worker's terminal transition — intentional
  `stop()`, restart-budget exhausted → `ERROR`, or a final unexpected `EXITED`
  with no further restart. A replaced `ERROR` worker has *already* released its
  permit at the moment it errored, so the replacing `start()` acquires a fresh
  one (net zero), and `stop()` on an already-terminal worker must not release a
  second time.

`Semaphore.release()` is not idempotent (a spurious release inflates the permit
count), so each `ManagedWorker` guards release with an at-most-once flag (e.g. an
`AtomicBoolean`). The count is authoritative (the semaphore), not derived by
scanning the map — so concurrent starts of different sources cannot overshoot the
cap.

### 3. `RuntimeCapacityException` (`platform.runtime`)

Lives in the `platform` module alongside the `RuntimeController` port — same
dependency direction as `CaptureException` in `platform.capture` (api →
platform; supervisor → platform). The supervisor throws it; the API maps it.

Message form:
`"concurrent-source cap reached (50); stop a running source or raise
iotsim.runtime.governance.max-concurrent-workers"` (the limit value is
interpolated).

### 4. `GlobalExceptionHandler` mapping

Add `@ExceptionHandler(RuntimeCapacityException.class)` → **503 Service
Unavailable**, returned as an RFC 9457 `ProblemDetail` in the existing style
(consistent with the `CaptureException` `UNAVAILABLE → 503` mapping). Semantics:
temporarily at capacity, retryable.

### 5. Config wiring

- `RuntimeProperties` gains a `Governance(Integer maxConcurrentWorkers)`
  sub-record and a `governancePolicy()` builder that applies
  `ResourceGovernancePolicy.DEFAULT` for unset fields (same shape as
  `restartPolicy()`).
- Property: `iotsim.runtime.governance.max-concurrent-workers`. Unset ⇒
  `DEFAULT` (50). `<= 0` ⇒ unlimited.
- `RuntimeConfig` passes the policy into the `Supervisor` constructor (a new
  constructor parameter / overload, following the existing constructor-overload
  pattern; `DEFAULT` used where governance is unspecified, e.g. existing tests).

## Components and boundaries

| Unit | Responsibility | Depends on |
|------|----------------|------------|
| `ResourceGovernancePolicy` | Value object: the cap + its unlimited sentinel | — |
| `Supervisor` admission gate | Enforce the cap on new starts; pair permits with map lifecycle | `Semaphore`, policy |
| `RuntimeCapacityException` | Signal "refused — at capacity" across the port | `platform.runtime` |
| `GlobalExceptionHandler` | Map the exception to 503 ProblemDetail | `platform.runtime` |
| `RuntimeProperties.Governance` | Bind config → policy | `ResourceGovernancePolicy` |

## Error handling

- Refused start: `RuntimeCapacityException` → 503; no worker spawned, no map
  entry, no permit consumed.
- Launch failure after admission: permit released (rollback); existing launch
  error propagates unchanged.
- Unlimited policy: gate disabled; behavior identical to today.

## Testing

- **`SupervisorGovernanceTest`** (unit, fake `WorkerLauncher`):
  - start up to the cap → all RUNNING; the next *new* start → `RuntimeCapacityException`.
  - stop one running source → a subsequent new start succeeds (permit released).
  - replacing an `ERROR` worker does not leak or double-count a permit.
  - **restart-with-backoff of an at-cap worker is not refused** (reuses its permit).
  - idempotent re-start of an active source takes no permit (cap of 1 still allows it).
  - launch failure after admission releases the permit (next start succeeds at cap 1).
  - unlimited (`maxConcurrentWorkers <= 0`) never refuses.
- **`RuntimePropertiesTest`**: binding of `governance.max-concurrent-workers`,
  default = 50, sentinel `<= 0` = unlimited.
- **`GlobalExceptionHandlerTest`**: `RuntimeCapacityException` → 503 ProblemDetail.
- `./gradlew build` green before PR ([[always-compile-and-test]]).

## Spec / docs touch

`02 §5` already specifies this behavior — **no governance-spec change**. The
chosen default (50), the property name, and the unlimited sentinel are documented
in the `RuntimeProperties` Javadoc, kept in one place per AGENTS.md.
