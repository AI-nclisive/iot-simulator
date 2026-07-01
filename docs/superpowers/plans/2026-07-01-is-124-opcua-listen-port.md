# IS-124 OPC UA listen port + port uniqueness — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make a simulated source's OPC UA listen port an input read from `runtimeConfig.listenPort`, bound deterministically by the worker, and reject starting a source on a port already held by any RUNNING source (host-wide) — unblocking IS-123's turnkey local endpoint.

**Architecture:** `RuntimeStartSpecs` reads `listenPort` from the source `runtimeConfig` (JSON) instead of hardcoding `0`; the three spec-build sites (`DataSourceService.start`, `SyntheticRunService`, `ReplayService`) pass it through. `DataSourceService.start` enforces host-wide port uniqueness via a new `DataSourceRepository.findAll()` + the runtime state, throwing `PortInUseException` (→ 409). No DB migration (`runtimeConfig` is an existing opaque JSON column).

**Tech Stack:** Java 21, Spring Boot, jOOQ, Jackson 3 (`tools.jackson.*`), JUnit 5 + AssertJ, Testcontainers (Postgres 17).

## Global Constraints

- Jackson 3: `tools.jackson.databind.{ObjectMapper,JsonNode}`, `tools.jackson.core.JacksonException` (unchecked). NEVER `com.fasterxml.jackson.*`.
- Listen port lives in `runtimeConfig.listenPort` (JSON int). Absent/blank/unparseable/≤0 → `0` (ephemeral) — preserves current behavior. The port parse NEVER throws (degrades to 0).
- Uniqueness is host-wide (across all projects), enforced at `DataSourceService.start`: reject if any *other* RUNNING source has the same non-zero port. Ephemeral (`0`) skips the check.
- `runtime.state(id)` returns a String; RUNNING is the literal `"RUNNING"`.
- `@Service`/`@Repository`, constructor injection. No DB migration.
- After all tasks: `./gradlew build` green. Catalog line for IS-124 added + checked in `backend-specs/TASKS.md` via `/open-pr` (the line does not exist on master yet — add it as `[x]` in the PR; catalog-sync passes because the IS-124 line is edited).

---

## File Structure

```
persistence/.../datasource/
  DataSourceRepository.java     (modify) — add default findAll()
  JooqDataSourceRepository.java (modify) — override findAll()
persistence/src/test/.../datasource/DataSourceRepositoryIT.java (modify) — findAll test
domain/.../datasource/
  RuntimeStartSpecs.java        (modify) — listenPort(...) + of(...,ObjectMapper)
  DataSourceService.java        (modify) — ObjectMapper ctor param; port uniqueness in start()
domain/.../replay/ReplayService.java        (modify) — inline spec uses listenPort(...)
domain/.../synthetic/SyntheticRunService.java (modify) — of(...,json)
domain/.../common/PortInUseException.java    (create) — → 409
domain/src/test/.../datasource/{DataSourceServiceTest,RuntimeStartSpecsTest}.java (modify/create)
domain/src/test/.../scan/ScanServiceTest.java, project/ProjectOverviewServiceTest.java (modify) — ctor ripple
api/.../error/GlobalExceptionHandler.java    (modify) — map PortInUseException → 409
```

---

## Task 1: `DataSourceRepository.findAll()`

**Files:**
- Modify: `persistence/src/main/java/com/ainclusive/iotsim/persistence/datasource/DataSourceRepository.java`
- Modify: `persistence/src/main/java/com/ainclusive/iotsim/persistence/datasource/JooqDataSourceRepository.java`
- Test: `persistence/src/test/java/com/ainclusive/iotsim/persistence/datasource/DataSourceRepositoryIT.java`

**Interfaces:**
- Produces: `default List<DataSourceRow> DataSourceRepository.findAll()` (host-wide, newest first); `JooqDataSourceRepository` overrides it with the real query. The default returns `List.of()` so existing fake implementors in other test suites need no change.

- [ ] **Step 1: Add the default method to the interface**

In `DataSourceRepository.java`, add (imports `java.util.List` already present):
```java
    /** All data sources across all projects, newest first. Host-wide (used for port-uniqueness). */
    default List<DataSourceRow> findAll() {
        return List.of();
    }
```

- [ ] **Step 2: Write the failing IT**

Add to `DataSourceRepositoryIT.java` (reuse its existing `dataSources` repo + a way to insert; mirror the file's existing insert/setup helpers — it already creates sources under a project):
```java
    @Test
    void findAllReturnsSourcesAcrossProjects() {
        // The IT already has a project + repo wired; create a second project if the file has a helper,
        // otherwise insert two sources under the existing project(s).
        DataSourceRow a = dataSources.insert(projectId, "A", "OPC_UA", "MANUAL", null, null, "it");
        DataSourceRow b = dataSources.insert(projectId, "B", "OPC_UA", "MANUAL", null, null, "it");

        List<DataSourceRow> all = dataSources.findAll();

        assertThat(all).extracting(DataSourceRow::id).contains(a.id(), b.id());
    }
```
(Match the real `insert(...)` signature in this repo — check `DataSourceRepository.insert` params and adjust the call.)

- [ ] **Step 3: Run the IT to verify it fails**

Run:
```bash
DOCKER_HOST=unix://$HOME/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
./gradlew :persistence:test --tests '*DataSourceRepositoryIT' --rerun-tasks
```
Expected: FAIL — `findAll()` returns the default empty list, so `contains(a.id(), b.id())` fails.

- [ ] **Step 4: Override `findAll()` in `JooqDataSourceRepository`**

Mirror the existing `findByProject` query, without the project filter:
```java
    @Override
    public List<DataSourceRow> findAll() {
        return dsl.selectFrom(DATA_SOURCES)
                .orderBy(DATA_SOURCES.CREATED_AT.desc())
                .fetch()
                .map(this::map);
    }
```

- [ ] **Step 5: Run the IT to verify it passes**

Run:
```bash
DOCKER_HOST=unix://$HOME/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
./gradlew :persistence:test --tests '*DataSourceRepositoryIT' --rerun-tasks
```
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add persistence/src/main/java/com/ainclusive/iotsim/persistence/datasource \
        persistence/src/test/java/com/ainclusive/iotsim/persistence/datasource/DataSourceRepositoryIT.java
git commit -m "feat(IS-124): DataSourceRepository.findAll() (host-wide enumeration)"
```

---

## Task 2: `RuntimeStartSpecs` reads listen port; wire the three build sites

**Files:**
- Modify: `domain/src/main/java/com/ainclusive/iotsim/domain/datasource/RuntimeStartSpecs.java`
- Modify: `domain/src/main/java/com/ainclusive/iotsim/domain/datasource/DataSourceService.java`
- Modify: `domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/SyntheticRunService.java`
- Modify: `domain/src/main/java/com/ainclusive/iotsim/domain/replay/ReplayService.java`
- Modify (ctor ripple): `domain/src/test/java/com/ainclusive/iotsim/domain/datasource/DataSourceServiceTest.java`, `.../scan/ScanServiceTest.java`, `.../project/ProjectOverviewServiceTest.java`
- Test: `domain/src/test/java/com/ainclusive/iotsim/domain/datasource/RuntimeStartSpecsTest.java`

**Interfaces:**
- Consumes: `DataSourceRow.runtimeConfig()`; `RuntimeStartSpec` ctor.
- Produces:
  - `public static int RuntimeStartSpecs.listenPort(String runtimeConfig, ObjectMapper json)` — tolerant parse, `0` on any problem or ≤0.
  - `RuntimeStartSpecs.of(SchemaRepository, DataSourceRow, ObjectMapper)` and `of(SchemaRepository, DataSourceRow, DeterministicSettings, ObjectMapper)` — the old 2-/3-arg overloads are replaced by these (the extra `ObjectMapper`).
  - `DataSourceService` constructor gains a trailing `ObjectMapper json` parameter.

- [ ] **Step 1: Write the failing `RuntimeStartSpecs` unit test**

`RuntimeStartSpecsTest.java`:
```java
package com.ainclusive.iotsim.domain.datasource;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class RuntimeStartSpecsTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void listenPortParsedFromRuntimeConfig() {
        assertThat(RuntimeStartSpecs.listenPort("{\"listenPort\":4840}", json)).isEqualTo(4840);
    }

    @Test
    void listenPortDefaultsToZeroWhenAbsentBlankOrInvalid() {
        assertThat(RuntimeStartSpecs.listenPort("{}", json)).isZero();
        assertThat(RuntimeStartSpecs.listenPort(null, json)).isZero();
        assertThat(RuntimeStartSpecs.listenPort("   ", json)).isZero();
        assertThat(RuntimeStartSpecs.listenPort("not-json", json)).isZero();
        assertThat(RuntimeStartSpecs.listenPort("{\"listenPort\":0}", json)).isZero();
        assertThat(RuntimeStartSpecs.listenPort("{\"listenPort\":-5}", json)).isZero();
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :domain:test --tests '*RuntimeStartSpecsTest'`
Expected: FAIL to compile — `listenPort` not defined.

- [ ] **Step 3: Implement `RuntimeStartSpecs`**

Replace `RuntimeStartSpecs.java` with:
```java
package com.ainclusive.iotsim.domain.datasource;

import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaWithNodes;
import com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec;
import com.ainclusive.iotsim.protocolmodel.DeterministicSettings;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Builds a {@link RuntimeStartSpec} from a data-source and its current schema. */
public final class RuntimeStartSpecs {

    private RuntimeStartSpecs() {}

    public static RuntimeStartSpec of(SchemaRepository schemas, DataSourceRow source, ObjectMapper json) {
        return of(schemas, source, null, json);
    }

    public static RuntimeStartSpec of(SchemaRepository schemas, DataSourceRow source,
            DeterministicSettings deterministicSettings, ObjectMapper json) {
        var current = schemas.findCurrent(source.id());
        return new RuntimeStartSpec(
                source.protocol(),
                current.map(SchemaWithNodes::version).orElse(0),
                current.map(SchemaWithNodes::nodes).orElse(List.of()),
                listenPort(source.runtimeConfig(), json),
                deterministicSettings);
    }

    /** Desired protocol listen port from {@code runtimeConfig.listenPort}; 0 (ephemeral) on
     *  null/blank/unparseable/≤0. Never throws. */
    public static int listenPort(String runtimeConfig, ObjectMapper json) {
        if (runtimeConfig == null || runtimeConfig.isBlank()) {
            return 0;
        }
        try {
            JsonNode root = json.readTree(runtimeConfig);
            JsonNode port = root.isObject() ? root.get("listenPort") : null;
            int value = port != null && port.isNumber() ? port.asInt() : 0;
            return Math.max(value, 0);
        } catch (JacksonException e) {
            return 0;
        }
    }
}
```
> Verify `JsonNode.isNumber()`/`asInt()` exist in this repo's Jackson 3 (they were confirmed for `asLong` in IS-086; `asInt` is the int sibling). If `asInt()` differs, use the compiling accessor.

- [ ] **Step 4: Wire `DataSourceService` (add ObjectMapper; use `of(...,json)`)**

In `DataSourceService.java`: add the field + ctor param + import `tools.jackson.databind.ObjectMapper`:
```java
    private final ObjectMapper json;

    public DataSourceService(DataSourceRepository dataSources, ProjectRepository projects,
            SchemaRepository schemas, RuntimeController runtime, CredentialStore credentials,
            ObjectMapper json) {
        this.dataSources = dataSources;
        this.projects = projects;
        this.schemas = schemas;
        this.runtime = runtime;
        this.credentials = credentials;
        this.json = json;
    }
```
Update `start(...)` to pass `json` (the uniqueness check is added in Task 3):
```java
    public DataSource start(String projectId, String id) {
        DataSourceRow row = requireRow(projectId, id);
        runtime.start(id, RuntimeStartSpecs.of(schemas, row, json));
        return map(row);
    }
```

- [ ] **Step 5: Wire `SyntheticRunService` and `ReplayService`**

`SyntheticRunService.java` line ~100: `runtime.start(dataSourceId, RuntimeStartSpecs.of(schemas, source, json));`

`ReplayService.java`: it builds the spec inline (line ~112) with a hardcoded `0`. Add `import com.ainclusive.iotsim.domain.datasource.RuntimeStartSpecs;` and change the port arg:
```java
            RuntimeStartSpec startSpec = new RuntimeStartSpec(
                    source.protocol(),
                    currentSchemaVersion,
                    currentSchema.map(SchemaWithNodes::nodes).orElse(List.of()),
                    RuntimeStartSpecs.listenPort(source.runtimeConfig(), json),
                    settings);
```

- [ ] **Step 6: Fix the three `DataSourceService` test constructors**

In `DataSourceServiceTest.java`, `ScanServiceTest.java`, `ProjectOverviewServiceTest.java`: every `new DataSourceService(...)` gains a trailing `new ObjectMapper()` argument (import `tools.jackson.databind.ObjectMapper`). `DataSourceServiceTest` builds it in 3 places (setUp + two inline) — update all.

- [ ] **Step 7: Run domain tests to verify green**

Run: `./gradlew :domain:test --tests '*RuntimeStartSpecsTest' --tests '*DataSourceServiceTest' --tests '*ScanServiceTest' --tests '*ProjectOverviewServiceTest' --tests '*ReplayServiceTest' --tests '*SyntheticRunServiceTest'`
Expected: PASS (new `RuntimeStartSpecsTest` passes; existing tests still green through the ctor/port changes).

- [ ] **Step 8: Commit**

```bash
git add domain/src/main/java/com/ainclusive/iotsim/domain/datasource/RuntimeStartSpecs.java \
        domain/src/main/java/com/ainclusive/iotsim/domain/datasource/DataSourceService.java \
        domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/SyntheticRunService.java \
        domain/src/main/java/com/ainclusive/iotsim/domain/replay/ReplayService.java \
        domain/src/test/java/com/ainclusive/iotsim/domain/datasource/RuntimeStartSpecsTest.java \
        domain/src/test/java/com/ainclusive/iotsim/domain/datasource/DataSourceServiceTest.java \
        domain/src/test/java/com/ainclusive/iotsim/domain/scan/ScanServiceTest.java \
        domain/src/test/java/com/ainclusive/iotsim/domain/project/ProjectOverviewServiceTest.java
git commit -m "feat(IS-124): read OPC UA listen port from runtimeConfig at all start paths"
```

---

## Task 3: Port uniqueness at start + `PortInUseException` + 409 mapping

**Files:**
- Create: `domain/src/main/java/com/ainclusive/iotsim/domain/common/PortInUseException.java`
- Modify: `domain/src/main/java/com/ainclusive/iotsim/domain/datasource/DataSourceService.java`
- Modify: `api/src/main/java/com/ainclusive/iotsim/api/error/GlobalExceptionHandler.java`
- Test: `domain/src/test/java/com/ainclusive/iotsim/domain/datasource/DataSourceServiceTest.java`

**Interfaces:**
- Consumes: `DataSourceRepository.findAll()` (Task 1); `RuntimeStartSpecs.listenPort` (Task 2); `runtime.state(id)`.
- Produces: `PortInUseException(int port, String conflictingSourceId)` in `com.ainclusive.iotsim.domain.common`, mapped to HTTP 409.

- [ ] **Step 1: Write the exception**

`PortInUseException.java`:
```java
package com.ainclusive.iotsim.domain.common;

/** A source cannot start because its listen port is already bound by another running source (→ 409). */
public class PortInUseException extends RuntimeException {
    public PortInUseException(int port, String conflictingSourceId) {
        super("listen port " + port + " is already in use by running source " + conflictingSourceId);
    }
}
```

- [ ] **Step 2: Write the failing uniqueness tests**

In `DataSourceServiceTest.java`, first add a `findAll()` override to the in-file `InMemoryDataSourceRepository` fake (return its stored rows):
```java
        @Override
        public List<DataSourceRow> findAll() {
            return List.copyOf(rows.values());  // match the fake's backing collection name
        }
```
Then add tests (the fake `InMemoryRuntimeController` sets a source RUNNING on `start`; create sources with a `runtimeConfig` carrying `listenPort`):
```java
    @Test
    void startRejectsPortHeldByAnotherRunningSource() {
        DataSource a = service.create(PROJECT, "A", "OPC_UA", "MANUAL", null,
                "{\"listenPort\":4840}", null, null, "it");
        DataSource b = service.create(PROJECT, "B", "OPC_UA", "MANUAL", null,
                "{\"listenPort\":4840}", null, null, "it");
        service.start(PROJECT, a.id());   // A now RUNNING on 4840

        assertThatThrownBy(() -> service.start(PROJECT, b.id()))
                .isInstanceOf(PortInUseException.class);
    }

    @Test
    void startAllowsSamePortWhenOtherSourceIsStopped() {
        DataSource a = service.create(PROJECT, "A", "OPC_UA", "MANUAL", null,
                "{\"listenPort\":4840}", null, null, "it");
        DataSource b = service.create(PROJECT, "B", "OPC_UA", "MANUAL", null,
                "{\"listenPort\":4840}", null, null, "it");
        service.start(PROJECT, a.id());
        service.stop(PROJECT, a.id());    // A STOPPED → 4840 free

        service.start(PROJECT, b.id());   // no throw
        assertThat(service.get(PROJECT, b.id()).runtimeState()).isEqualTo(RuntimeState.RUNNING);
    }

    @Test
    void ephemeralPortNeverConflicts() {
        DataSource a = service.create(PROJECT, "A", "OPC_UA", "MANUAL", null, null, null, null, "it");
        DataSource b = service.create(PROJECT, "B", "OPC_UA", "MANUAL", null, null, null, null, "it");
        service.start(PROJECT, a.id());
        service.start(PROJECT, b.id());   // both ephemeral (0) → no throw
        assertThat(service.get(PROJECT, b.id()).runtimeState()).isEqualTo(RuntimeState.RUNNING);
    }

    @Test
    void restartingSameSourceIsNotSelfConflict() {
        DataSource a = service.create(PROJECT, "A", "OPC_UA", "MANUAL", null,
                "{\"listenPort\":4840}", null, null, "it");
        service.start(PROJECT, a.id());
        service.start(PROJECT, a.id());   // same id re-start → no throw
        assertThat(service.get(PROJECT, a.id()).runtimeState()).isEqualTo(RuntimeState.RUNNING);
    }
```
> Confirm the `create(...)` arg order against `DataSourceService.create` (projectId, name, protocol, basis, endpoint, runtimeConfig, credentials, actor) — the `runtimeConfig` JSON is the 6th arg. Adjust if the fake stores `runtimeConfig` under a different accessor.

- [ ] **Step 3: Run to verify they fail**

Run: `./gradlew :domain:test --tests '*DataSourceServiceTest'`
Expected: FAIL — `startRejectsPortHeldByAnotherRunningSource` does not throw (no uniqueness check yet).

- [ ] **Step 4: Add the uniqueness check to `DataSourceService.start`**

```java
    public DataSource start(String projectId, String id) {
        DataSourceRow row = requireRow(projectId, id);
        int port = RuntimeStartSpecs.listenPort(row.runtimeConfig(), json);
        if (port != 0) {
            for (DataSourceRow other : dataSources.findAll()) {
                if (!other.id().equals(id)
                        && "RUNNING".equals(runtime.state(other.id()))
                        && RuntimeStartSpecs.listenPort(other.runtimeConfig(), json) == port) {
                    throw new PortInUseException(port, other.id());
                }
            }
        }
        runtime.start(id, RuntimeStartSpecs.of(schemas, row, json));
        return map(row);
    }
```
Import `com.ainclusive.iotsim.domain.common.PortInUseException`.

- [ ] **Step 5: Map `PortInUseException` → 409 in `GlobalExceptionHandler`**

Add the import `com.ainclusive.iotsim.domain.common.PortInUseException` and a handler (beside `conflict(...)`):
```java
    @ExceptionHandler(PortInUseException.class)
    public ProblemDetail portInUse(PortInUseException e) {
        return problem(HttpStatus.CONFLICT, e.getMessage());
    }
```

- [ ] **Step 6: Run to verify green**

Run: `./gradlew :domain:test --tests '*DataSourceServiceTest' && ./gradlew :api:compileJava`
Expected: PASS (all four uniqueness tests + existing) and api compiles.

- [ ] **Step 7: Commit**

```bash
git add domain/src/main/java/com/ainclusive/iotsim/domain/common/PortInUseException.java \
        domain/src/main/java/com/ainclusive/iotsim/domain/datasource/DataSourceService.java \
        domain/src/test/java/com/ainclusive/iotsim/domain/datasource/DataSourceServiceTest.java \
        api/src/main/java/com/ainclusive/iotsim/api/error/GlobalExceptionHandler.java
git commit -m "feat(IS-124): reject start on a port held by another running source (409)"
```

---

## Task 4: Full verification

- [ ] **Step 1: Full build** ([[always-compile-and-test]])

Run (Colima env so ITs run):
```bash
DOCKER_HOST=unix://$HOME/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
./gradlew build --rerun-tasks
```
Expected: BUILD SUCCESSFUL — `DataSourceRepositoryIT` (incl. `findAll`), `RuntimeStartSpecsTest`, `DataSourceServiceTest` (incl. uniqueness), and the existing replay/synthetic/scan/project tests all pass; app context-boot smoke wires `DataSourceService` with the new `ObjectMapper` arg (Spring auto-wires the bean).

- [ ] **Step 2: Open the PR**

Hand off to `/open-pr` — it adds + checks the `IS-124` line in `backend-specs/TASKS.md` (the line is new; add it as `[x]`), creates the PR with `Implements: IS-124`, arms squash auto-merge, moves the board to In review.

---

## Self-Review

**Spec coverage:**
- Listen port from `runtimeConfig.listenPort` → Task 2 (`RuntimeStartSpecs.listenPort` + all three build sites). ✅
- Absent/invalid/≤0 → 0 (ephemeral) → Task 2 (`listenPort` parse + test). ✅
- Host-wide uniqueness at start → Task 1 (`findAll`) + Task 3 (scan + `PortInUseException`). ✅
- Ephemeral skips check; STOPPED doesn't block; different project conflicts; self-restart OK → Task 3 tests. ✅
- 409 mapping → Task 3 (`GlobalExceptionHandler`). ✅
- No migration → confirmed (runtimeConfig existing column). ✅
- Out of scope (port-range, IPC/API surfacing, `/run-local`, endpoint→connectionConfig) → not implemented. ✅

**Placeholder scan:** No TBD/TODO. The `asInt`/`isNumber` verify note (Task 2) and the `create(...)`/`insert(...)` arg-order + fake-collection-name confirm notes are explicit verify-or-adjust instructions, not placeholders.

**Type consistency:** `RuntimeStartSpecs.listenPort(String, ObjectMapper) -> int` and `of(..., ObjectMapper)` used identically in `DataSourceService` (Tasks 2/3), `SyntheticRunService`, `ReplayService`. `DataSourceService` ctor gains the trailing `ObjectMapper json` (Task 2) — the 3 test ctors updated in the same task. `DataSourceRepository.findAll()` (Task 1, default) overridden by Jooq (Task 1) and by the `DataSourceServiceTest` in-file fake (Task 3). `PortInUseException(int, String)` (Task 3) used in `DataSourceService.start` and mapped in `GlobalExceptionHandler`. Host-wide test (different project) is covered by `findAll()` + the fake returning all rows. ✅

> Note: a different-project conflict test would require the `DataSourceServiceTest` fake project repo to accept a second project id; the four Task-3 tests use one project, which still exercises host-wide `findAll()` (the check does not filter by project). A cross-project test is optional polish, not required for correctness.
