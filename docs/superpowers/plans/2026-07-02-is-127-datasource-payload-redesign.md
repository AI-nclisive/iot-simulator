# IS-127 — Data-source payload redesign (Plan 1: persistence + domain + API)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the overloaded data-source `endpoint` field with a first-class, uniqueness-enforced `simulatorPort` (local serve port) plus a nullable `realDeviceEndpoint` (real device address for scan/capture), and return a derived read-only `serveUrl`.

**Architecture:** `simulatorPort` becomes a real `int` column (single source of truth; `listenPort` leaves `runtimeConfig`). `endpoint` is renamed to `real_device_endpoint` and made nullable. `serveUrl` is computed at the domain layer from a configured advertised host + protocol scheme/path + port. The IS-124 port-uniqueness check re-points from the `runtimeConfig.listenPort` JSON parse to the new column. jOOQ tables regenerate from the Flyway migration.

**Tech Stack:** Java 21, Spring Boot 4, jOOQ (DDLDatabase codegen from migration SQL), Flyway, Postgres, JUnit 5 + AssertJ, Testcontainers (repository/migration ITs).

**Scope of this plan:** persistence, domain, API. The worker/supervisor **bind-failure surfacing** is a separate subsystem with different test infrastructure — see the companion plan `2026-07-02-is-127-bind-failure-surfacing.md` (execute after this one, same branch).

## Global Constraints

- Branch: `feat/IS-127-datasource-payload-redesign` (already created & linked to issue #359).
- Stay on `/api/v1`. Do not create a new API version. (memory: api-version-only-bump-on-request)
- Jackson 3: imports are `tools.jackson.*`; unchecked `JacksonException`. (memory: jackson-3-packages)
- Per-protocol default port: `OPC_UA` → **4840**, `MODBUS_TCP` → **502**.
- `simulatorPort` valid range: **1..65535**.
- `real_device_endpoint` stays a `jsonb` column; keep the existing JSON-string-scalar encoding (`endpointToJsonb`/`endpointFromJsonb`) — only the column name and nullability change.
- Every commit message ends with the trailer:
  `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`
- ITs (Testcontainers) skip silently under plain `./gradlew build` unless `DOCKER_HOST` + `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` are exported for Colima. (memory: testcontainers-colima-env)
- Run a module `:check` or full `build` before the PR — `--tests X` skips Checkstyle/Spotless. (memory: checkstyle-runs-only-in-full-build)

---

## File Structure

**Persistence**
- `persistence/src/main/resources/db/migration/V9__datasource_simulator_port.sql` — CREATE. The migration.
- `persistence/src/main/java/.../persistence/datasource/DataSourceRow.java` — MODIFY. `+Integer simulatorPort`, rename `endpoint`→`realDeviceEndpoint`.
- `persistence/src/main/java/.../persistence/datasource/DataSourceRepository.java` — MODIFY. `insert`/`update` signatures.
- `persistence/src/main/java/.../persistence/datasource/JooqDataSourceRepository.java` — MODIFY. Map/insert/update/duplicate.
- `persistence/src/test/java/.../persistence/datasource/DataSourceRepositoryIT.java` — MODIFY. Round-trip new fields.

**Domain**
- `domain/src/main/java/.../domain/datasource/SimulatorUrl.java` — CREATE. Protocol→scheme/path/default-port + serve URL.
- `domain/src/main/java/.../domain/datasource/RuntimeStartSpecs.java` — MODIFY. Read the column; drop the JSON `listenPort` helper.
- `domain/src/main/java/.../domain/datasource/DataSource.java` — MODIFY. `+int simulatorPort`, rename `endpoint`→`realDeviceEndpoint`, `+String serveUrl`.
- `domain/src/main/java/.../domain/datasource/DataSourceService.java` — MODIFY. create/update signatures, `map()` computes `serveUrl`, `start()` uniqueness on the column, advertised-host config.
- `domain/src/main/java/.../domain/recording/RecordingService.java` — MODIFY. Read `realDeviceEndpoint`, new message.
- `domain/src/main/java/.../domain/scan/ScanService.java` — MODIFY. `createFromScan` passes `realDeviceEndpoint` + null port.
- `domain/src/main/java/.../domain/project/ProjectService.java` — MODIFY. Duplicate-project copy uses new fields.
- `domain/src/main/java/.../domain/io/ProjectZipExporter.java` — MODIFY. Export both fields.
- `domain/src/main/java/.../domain/io/ProjectImportService.java` — MODIFY. Import both fields.
- `domain/src/test/java/.../domain/datasource/SimulatorUrlTest.java` — CREATE.
- `domain/src/test/java/.../domain/datasource/RuntimeStartSpecsTest.java` — MODIFY.
- `domain/src/test/java/.../domain/datasource/DataSourceServiceTest.java` — MODIFY (signatures + port tests).

**API**
- `api/src/main/java/.../api/datasource/DataSourceController.java` — MODIFY. DTOs + create/update calls.
- `app/src/main/resources/application.yml` — MODIFY. `iotsim.simulator.advertised-host`.
- `api/src/test/java/.../api/datasource/DataSourceControllerTest.java` (or the existing web test) — MODIFY.

---

## Task 1: Flyway migration + jOOQ regeneration

**Files:**
- Create: `persistence/src/main/resources/db/migration/V9__datasource_simulator_port.sql`

**Interfaces:**
- Produces (via jOOQ codegen): `DATA_SOURCES.SIMULATOR_PORT` (`Field<Integer>`), `DATA_SOURCES.REAL_DEVICE_ENDPOINT` (`Field<JSONB>`), and the removal of `DATA_SOURCES.ENDPOINT`.

> Use `/flyway-migration datasource_simulator_port` to get a collision-safe version if `V9` is already taken by a parallel branch. This plan assumes `V9`.

- [ ] **Step 1: Write the migration**

Create `persistence/src/main/resources/db/migration/V9__datasource_simulator_port.sql`. The DDL statements (add/rename/alter) are parsed by the jOOQ `DDLDatabase` codegen; the Postgres-only backfill DML uses jsonb operators H2 can't parse, so it is wrapped in `[jooq ignore start/stop]` markers — Flyway still runs it against Postgres, codegen skips it.

```sql
-- IS-127: split the overloaded data-source endpoint into a first-class
-- simulator_port (serve port) and a nullable real_device_endpoint (real device).

-- 1. New serve-port column (nullable until backfilled).
alter table data_sources add column simulator_port int;

-- 2. Backfill from runtime_config.listenPort, else per-protocol default; then
--    drop the migrated key. Postgres-only jsonb operators — skipped by jOOQ codegen.
-- [jooq ignore start]
update data_sources
   set simulator_port = coalesce(
        nullif(runtime_config->>'listenPort', '')::int,
        case protocol when 'MODBUS_TCP' then 502 else 4840 end);
update data_sources
   set runtime_config = runtime_config - 'listenPort';
-- [jooq ignore stop]

-- 3. Serve port is now always known.
alter table data_sources alter column simulator_port set not null;

-- 4. Rename endpoint -> real_device_endpoint and make it nullable.
alter table data_sources rename column endpoint to real_device_endpoint;
alter table data_sources alter column real_device_endpoint drop not null;
alter table data_sources alter column real_device_endpoint drop default;

-- 5. A real-device address is meaningless for synthetic sources — null them out.
-- [jooq ignore start]
update data_sources set real_device_endpoint = null where basis = 'SYNTHETIC';
-- [jooq ignore stop]
```

- [ ] **Step 2: Regenerate jOOQ and verify the new fields exist**

Run: `./gradlew :persistence:generateJooq`
Expected: BUILD SUCCESSFUL.

Then confirm the generated table has the new fields and dropped the old one:

Run: `grep -E "SIMULATOR_PORT|REAL_DEVICE_ENDPOINT|public final TableField.*ENDPOINT" persistence/build/generated/jooq/com/ainclusive/iotsim/persistence/jooq/tables/DataSources.java`
Expected: lines for `SIMULATOR_PORT` (Integer) and `REAL_DEVICE_ENDPOINT` (JSONB); **no** bare `ENDPOINT` field.

If codegen fails on a DDL statement (parser limitation), split that statement or widen the `[jooq ignore start/stop]` block around it, then re-run.

- [ ] **Step 3: Commit**

```bash
git add persistence/src/main/resources/db/migration/V9__datasource_simulator_port.sql
git commit -m "feat(IS-127): migration — simulator_port column + real_device_endpoint rename

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: DataSourceRow + repository

**Files:**
- Modify: `persistence/.../datasource/DataSourceRow.java`
- Modify: `persistence/.../datasource/DataSourceRepository.java`
- Modify: `persistence/.../datasource/JooqDataSourceRepository.java`
- Test: `persistence/src/test/java/.../datasource/DataSourceRepositoryIT.java`

**Interfaces:**
- Produces:
  - `DataSourceRow(... , int simulatorPort, String realDeviceEndpoint, String runtimeConfig, ...)` — note `endpoint` is renamed and a new `simulatorPort` is inserted.
  - `DataSourceRepository.insert(String projectId, String name, String protocol, String basis, int simulatorPort, String realDeviceEndpoint, String runtimeConfigJson, String createdBy)`
  - `DataSourceRepository.update(String id, String name, int simulatorPort, String realDeviceEndpoint, String runtimeConfigJson, boolean enabled, long expectedVersion)`

- [ ] **Step 1: Update `DataSourceRow`**

Replace the record components — insert `simulatorPort` after `schemaVersion`, rename `endpoint`→`realDeviceEndpoint`:

```java
public record DataSourceRow(
        String id,
        String projectId,
        String name,
        String protocol,
        String basis,
        String schemaId,
        Integer schemaVersion,
        int simulatorPort,
        String realDeviceEndpoint,
        String runtimeConfig,
        boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String createdBy,
        long version) {
}
```

- [ ] **Step 2: Update the repository interface**

`DataSourceRepository.java` — new `insert`/`update` signatures:

```java
    DataSourceRow insert(String projectId, String name, String protocol, String basis,
            int simulatorPort, String realDeviceEndpoint, String runtimeConfigJson, String createdBy);

    Optional<DataSourceRow> update(String id, String name, int simulatorPort,
            String realDeviceEndpoint, String runtimeConfigJson, boolean enabled, long expectedVersion);
```

- [ ] **Step 3: Update `JooqDataSourceRepository`**

Rename the endpoint jsonb helpers' target column, add the port, and re-map. Changes:

`insert(...)` body:
```java
    @Override
    public DataSourceRow insert(String projectId, String name, String protocol, String basis,
            int simulatorPort, String realDeviceEndpoint, String runtimeConfigJson, String createdBy) {
        DataSourcesRecord record = dsl.insertInto(DATA_SOURCES)
                .set(DATA_SOURCES.ID, Ids.newId())
                .set(DATA_SOURCES.PROJECT_ID, projectId)
                .set(DATA_SOURCES.NAME, name)
                .set(DATA_SOURCES.PROTOCOL, protocol)
                .set(DATA_SOURCES.BASIS, basis)
                .set(DATA_SOURCES.SIMULATOR_PORT, simulatorPort)
                .set(DATA_SOURCES.REAL_DEVICE_ENDPOINT, endpointToJsonb(realDeviceEndpoint))
                .set(DATA_SOURCES.RUNTIME_CONFIG, json(runtimeConfigJson))
                .set(DATA_SOURCES.CREATED_BY, createdBy)
                .returning()
                .fetchOne();
        return map(record);
    }
```

`update(...)` body:
```java
    @Override
    public Optional<DataSourceRow> update(String id, String name, int simulatorPort,
            String realDeviceEndpoint, String runtimeConfigJson, boolean enabled, long expectedVersion) {
        DataSourcesRecord record = dsl.update(DATA_SOURCES)
                .set(DATA_SOURCES.NAME, name)
                .set(DATA_SOURCES.SIMULATOR_PORT, simulatorPort)
                .set(DATA_SOURCES.REAL_DEVICE_ENDPOINT, endpointToJsonb(realDeviceEndpoint))
                .set(DATA_SOURCES.RUNTIME_CONFIG, json(runtimeConfigJson))
                .set(DATA_SOURCES.ENABLED, enabled)
                .set(DATA_SOURCES.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .set(DATA_SOURCES.VERSION, DATA_SOURCES.VERSION.plus(1))
                .where(DATA_SOURCES.ID.eq(id).and(DATA_SOURCES.VERSION.eq(expectedVersion)))
                .returning()
                .fetchOne();
        return Optional.ofNullable(record).map(this::map);
    }
```

`duplicate(...)` — copy the new columns:
```java
                .set(DATA_SOURCES.SIMULATOR_PORT, source.getSimulatorPort())
                .set(DATA_SOURCES.REAL_DEVICE_ENDPOINT, source.getRealDeviceEndpoint())
```
(replace the old `.set(DATA_SOURCES.ENDPOINT, source.getEndpoint())`.)

`map(...)` — new component order:
```java
    private DataSourceRow map(DataSourcesRecord r) {
        return new DataSourceRow(
                r.getId(),
                r.getProjectId(),
                r.getName(),
                r.getProtocol(),
                r.getBasis(),
                r.getSchemaId(),
                r.getSchemaVersion(),
                r.getSimulatorPort(),
                endpointFromJsonb(r.getRealDeviceEndpoint()),
                jsonString(r.getRuntimeConfig()),
                Boolean.TRUE.equals(r.getEnabled()),
                r.getCreatedAt(),
                r.getUpdatedAt(),
                r.getCreatedBy(),
                r.getVersion());
    }
```

Rename the two jsonb helper Javadocs to say "real device endpoint"; the `endpointToJsonb`/`endpointFromJsonb`/`quoteJson`/`unquoteJson` bodies are unchanged.

- [ ] **Step 4: Update `DataSourceRepositoryIT` and run it**

In the IT, change `insert(...)`/`update(...)` calls to the new signatures (pass a port, e.g. `4840`, and a nullable real-device endpoint), and assert round-trip:

```java
        DataSourceRow row = repo.insert(projectId, "Pump", "OPC_UA", "SCAN",
                4840, "opc.tcp://plc:4840", "{}", "local");
        assertThat(row.simulatorPort()).isEqualTo(4840);
        assertThat(row.realDeviceEndpoint()).isEqualTo("opc.tcp://plc:4840");

        DataSourceRow synthetic = repo.insert(projectId, "Sim", "OPC_UA", "SYNTHETIC",
                4841, null, "{}", "local");
        assertThat(synthetic.simulatorPort()).isEqualTo(4841);
        assertThat(synthetic.realDeviceEndpoint()).isNull();
```

Run (Colima env exported): `./gradlew :persistence:test --tests '*DataSourceRepositoryIT'`
Expected: PASS. (If Docker is unavailable the IT skips — verify it is not skipped before claiming done.)

- [ ] **Step 5: Commit**

```bash
git add persistence/src/main/java persistence/src/test/java
git commit -m "feat(IS-127): DataSourceRow + repository carry simulatorPort + realDeviceEndpoint

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: `SimulatorUrl` helper

**Files:**
- Create: `domain/src/main/java/.../domain/datasource/SimulatorUrl.java`
- Test: `domain/src/test/java/.../domain/datasource/SimulatorUrlTest.java`

**Interfaces:**
- Produces:
  - `SimulatorUrl.defaultPort(Protocol) -> int` (OPC_UA 4840, MODBUS_TCP 502)
  - `SimulatorUrl.of(Protocol protocol, String host, int port) -> String` serve URL

- [ ] **Step 1: Write the failing test**

`SimulatorUrlTest.java`:
```java
package com.ainclusive.iotsim.domain.datasource;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SimulatorUrlTest {

    @Test
    void opcUaUrlHasSchemeHostPortAndPath() {
        assertThat(SimulatorUrl.of(Protocol.OPC_UA, "plant.local", 4840))
                .isEqualTo("opc.tcp://plant.local:4840/iotsim");
    }

    @Test
    void modbusUrlHasSchemeHostPort() {
        assertThat(SimulatorUrl.of(Protocol.MODBUS_TCP, "plant.local", 502))
                .isEqualTo("modbus.tcp://plant.local:502");
    }

    @Test
    void defaultPortIsPerProtocol() {
        assertThat(SimulatorUrl.defaultPort(Protocol.OPC_UA)).isEqualTo(4840);
        assertThat(SimulatorUrl.defaultPort(Protocol.MODBUS_TCP)).isEqualTo(502);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :domain:test --tests '*SimulatorUrlTest'`
Expected: FAIL — `SimulatorUrl` does not exist / cannot find symbol.

- [ ] **Step 3: Write `SimulatorUrl`**

```java
package com.ainclusive.iotsim.domain.datasource;

/**
 * The single home of the protocol → (scheme, path, default port) mapping.
 * Builds the read-only "connect your client here" URL for a simulated source
 * from the deployment's advertised host and the source's serve port (IS-127).
 */
public final class SimulatorUrl {

    private SimulatorUrl() {}

    /** Default serve port per protocol, used when a source does not specify one. */
    public static int defaultPort(Protocol protocol) {
        return switch (protocol) {
            case OPC_UA -> 4840;
            case MODBUS_TCP -> 502;
        };
    }

    /** Client connect URL for a simulated source: {@code scheme://host:port[/path]}. */
    public static String of(Protocol protocol, String host, int port) {
        return switch (protocol) {
            case OPC_UA -> "opc.tcp://" + host + ":" + port + "/iotsim";
            case MODBUS_TCP -> "modbus.tcp://" + host + ":" + port;
        };
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :domain:test --tests '*SimulatorUrlTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/java/com/ainclusive/iotsim/domain/datasource/SimulatorUrl.java domain/src/test/java
git commit -m "feat(IS-127): SimulatorUrl — protocol scheme/path/default-port + serve URL

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: `RuntimeStartSpecs` reads the column

**Files:**
- Modify: `domain/.../datasource/RuntimeStartSpecs.java`
- Modify: `domain/.../synthetic/SyntheticRunService.java` (call site)
- Modify: `domain/.../replay/ReplayService.java` (call site, inline spec)
- Test: `domain/src/test/java/.../datasource/RuntimeStartSpecsTest.java`

**Interfaces:**
- Produces: `RuntimeStartSpecs.of(SchemaRepository, DataSourceRow, DeterministicSettings)` and `RuntimeStartSpecs.of(SchemaRepository, DataSourceRow)` — the `ObjectMapper` parameter and the `listenPort(String, ObjectMapper)` helper are **removed**. The port comes from `source.simulatorPort()`.

- [ ] **Step 1: Update `RuntimeStartSpecsTest`**

Replace the `listenPort`-parsing tests with a test that the spec's port comes from the row's `simulatorPort`. Build a `DataSourceRow` with the new component order:

```java
    @Test
    void listenPortComesFromSimulatorPortColumn() {
        DataSourceRow row = new DataSourceRow(
                "ds-1", "p", "Pump", "OPC_UA", "MANUAL", null, null,
                4840, null, "{}", false,
                java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now(), "local", 0);
        RuntimeStartSpec spec = RuntimeStartSpecs.of(new EmptySchemas(), row);
        assertThat(spec.listenPort()).isEqualTo(4840);
    }
```
(Provide a minimal `EmptySchemas implements SchemaRepository` returning `Optional.empty()` from `findCurrent`, like the existing test helpers. Delete the old `listenPort("{}")==0` / unparseable tests.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :domain:test --tests '*RuntimeStartSpecsTest'`
Expected: FAIL — `of(SchemaRepository, DataSourceRow)` signature / `DataSourceRow` constructor mismatch.

- [ ] **Step 3: Rewrite `RuntimeStartSpecs`**

```java
package com.ainclusive.iotsim.domain.datasource;

import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaWithNodes;
import com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec;
import com.ainclusive.iotsim.protocolmodel.DeterministicSettings;
import java.util.List;

/** Builds a {@link RuntimeStartSpec} from a data-source and its current schema. */
public final class RuntimeStartSpecs {

    private RuntimeStartSpecs() {}

    public static RuntimeStartSpec of(SchemaRepository schemas, DataSourceRow source) {
        return of(schemas, source, null);
    }

    public static RuntimeStartSpec of(SchemaRepository schemas, DataSourceRow source,
            DeterministicSettings deterministicSettings) {
        var current = schemas.findCurrent(source.id());
        return new RuntimeStartSpec(
                source.protocol(),
                current.map(SchemaWithNodes::version).orElse(0),
                current.map(SchemaWithNodes::nodes).orElse(List.of()),
                source.simulatorPort(),
                deterministicSettings);
    }
}
```

- [ ] **Step 4: Update the two call sites**

`SyntheticRunService.java` line ~111 — drop the `json` argument:
```java
            runtime.start(dataSourceId, RuntimeStartSpecs.of(schemas, source));
```
(Remove the now-unused `RuntimeStartSpecs` `json` usage; leave the field if used elsewhere, otherwise remove the import.)

`ReplayService.java` lines ~126-130 — the inline `new RuntimeStartSpec(...)` currently ends with `RuntimeStartSpecs.listenPort(source.runtimeConfig(), json)`. Replace that argument with `source.simulatorPort()`:
```java
            RuntimeStartSpec startSpec = new RuntimeStartSpec(
                    source.protocol(),
                    schemaVersion,
                    schemaNodes,
                    source.simulatorPort());
```
(Adjust to the exact local variable names present; remove the `RuntimeStartSpecs` import if it becomes unused.)

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :domain:test --tests '*RuntimeStartSpecsTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add domain/src
git commit -m "feat(IS-127): RuntimeStartSpecs reads simulatorPort column; drop runtimeConfig parse

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: `DataSource` record + `DataSourceService`

**Files:**
- Modify: `domain/.../datasource/DataSource.java`
- Modify: `domain/.../datasource/DataSourceService.java`
- Test: `domain/src/test/java/.../datasource/DataSourceServiceTest.java`

**Interfaces:**
- Produces:
  - `DataSource(... , int simulatorPort, String realDeviceEndpoint, String runtimeConfig, ... , String serveUrl, ...)` — see Step 1 for exact order.
  - `DataSourceService.create(String projectId, String name, String protocol, String basis, Integer simulatorPort, String realDeviceEndpoint, String runtimeConfig, ConnectionCredentials, List<SchemaNode> initialNodes, String actor)` — `simulatorPort` null → per-protocol default.
  - `DataSourceService.update(String projectId, String id, String name, Integer simulatorPort, String realDeviceEndpoint, String runtimeConfig, Boolean enabled, ConnectionCredentials, long expectedVersion)`
- Consumes: `SimulatorUrl` (Task 3), new `DataSourceRow`/repository (Task 2), `RuntimeStartSpecs.of(schemas, row, ...)` (Task 4).

- [ ] **Step 1: Update `DataSource` record**

```java
public record DataSource(
        String id,
        String projectId,
        String name,
        Protocol protocol,
        SourceBasis basis,
        String schemaId,
        Integer schemaVersion,
        int simulatorPort,
        String realDeviceEndpoint,
        String runtimeConfig,
        boolean enabled,
        RuntimeState runtimeState,
        CredentialState credentialState,
        String serveUrl,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        long version) {
}
```

- [ ] **Step 2: Update the port/behaviour tests in `DataSourceServiceTest`**

Replace every `service.create(...)`/`service.update(...)` call with the new signatures (see below), and rewrite the port-uniqueness tests. The `create` signature drops the old `endpoint` string and inserts `Integer simulatorPort` before `realDeviceEndpoint`. For the common no-endpoint case pass `null, null` for `simulatorPort, realDeviceEndpoint` (the service defaults the port per protocol).

Add the `advertisedHost` argument to the two `new DataSourceService(...)` constructions (pass `"localhost"` as the new last argument — see Step 4 for the constructor).

Rewrite the port tests:
```java
    @Test
    void startRejectsPortHeldByAnotherRunningSource() {
        DataSource a = service.create(PROJECT, "A", "OPC_UA", "MANUAL", 4840, null, null, null, null, "it");
        DataSource b = service.create(PROJECT, "B", "OPC_UA", "MANUAL", 4840, null, null, null, null, "it");
        service.start(PROJECT, a.id());
        assertThatThrownBy(() -> service.start(PROJECT, b.id()))
                .isInstanceOf(PortInUseException.class);
    }

    @Test
    void twoDefaultPortSourcesConflictOnStart() {
        // No explicit port → both default to 4840 → second start conflicts.
        DataSource a = service.create(PROJECT, "A", "OPC_UA", "MANUAL", null, null, null, null, null, "it");
        DataSource b = service.create(PROJECT, "B", "OPC_UA", "MANUAL", null, null, null, null, null, "it");
        service.start(PROJECT, a.id());
        assertThatThrownBy(() -> service.start(PROJECT, b.id()))
                .isInstanceOf(PortInUseException.class);
    }

    @Test
    void startAllowsDifferentPorts() {
        DataSource a = service.create(PROJECT, "A", "OPC_UA", "MANUAL", 4840, null, null, null, null, "it");
        DataSource b = service.create(PROJECT, "B", "OPC_UA", "MANUAL", 4841, null, null, null, null, "it");
        service.start(PROJECT, a.id());
        service.start(PROJECT, b.id());
        assertThat(service.get(PROJECT, b.id()).runtimeState()).isEqualTo(RuntimeState.RUNNING);
    }

    @Test
    void startAllowsSamePortWhenOtherSourceIsStopped() {
        DataSource a = service.create(PROJECT, "A", "OPC_UA", "MANUAL", 4840, null, null, null, null, "it");
        DataSource b = service.create(PROJECT, "B", "OPC_UA", "MANUAL", 4840, null, null, null, null, "it");
        service.start(PROJECT, a.id());
        service.stop(PROJECT, a.id());
        service.start(PROJECT, b.id());
        assertThat(service.get(PROJECT, b.id()).runtimeState()).isEqualTo(RuntimeState.RUNNING);
    }

    @Test
    void restartingSameSourceIsNotSelfConflict() {
        DataSource a = service.create(PROJECT, "A", "OPC_UA", "MANUAL", 4840, null, null, null, null, "it");
        service.start(PROJECT, a.id());
        service.start(PROJECT, a.id());
        assertThat(service.get(PROJECT, a.id()).runtimeState()).isEqualTo(RuntimeState.RUNNING);
    }

    @Test
    void createAppliesPerProtocolDefaultPort() {
        DataSource opc = service.create(PROJECT, "O", "OPC_UA", "MANUAL", null, null, null, null, null, "it");
        DataSource mod = service.create(PROJECT, "M", "MODBUS_TCP", "MANUAL", null, null, null, null, null, "it");
        assertThat(opc.simulatorPort()).isEqualTo(4840);
        assertThat(mod.simulatorPort()).isEqualTo(502);
    }

    @Test
    void createRejectsOutOfRangePort() {
        assertThatThrownBy(() -> service.create(PROJECT, "X", "OPC_UA", "MANUAL", 70000, null, null, null, null, "it"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void serveUrlIsDerivedFromHostAndPort() {
        DataSource ds = service.create(PROJECT, "O", "OPC_UA", "MANUAL", 4840, null, null, null, null, "it");
        assertThat(ds.serveUrl()).isEqualTo("opc.tcp://localhost:4840/iotsim");
    }
```

Also update the `InMemoryDataSourceRepository` fake: change `insert`/`update`/`duplicate` to the new signatures and store `simulatorPort` + `realDeviceEndpoint` (mirror the `DataSourceRow` order). Update the credential test that asserts `row.endpoint()` → `row.realDeviceEndpoint()`. Delete the old `ephemeralPortNeverConflicts` and `startThenStopTogglesRuntimeState` stays (uses default port now). Update `duplicate*` assertions from `endpoint()` to `realDeviceEndpoint()`.

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :domain:test --tests '*DataSourceServiceTest'`
Expected: FAIL — `create`/`update` signature mismatch, `DataSource` constructor mismatch.

- [ ] **Step 4: Rewrite `DataSourceService`**

Constructor gains `advertisedHost` (Spring injects via `@Value`):
```java
    private final String advertisedHost;

    public DataSourceService(DataSourceRepository dataSources, ProjectRepository projects,
            SchemaRepository schemas, RuntimeController runtime, CredentialStore credentials,
            ObjectMapper json,
            @org.springframework.beans.factory.annotation.Value("${iotsim.simulator.advertised-host:localhost}")
            String advertisedHost) {
        this.dataSources = dataSources;
        this.projects = projects;
        this.schemas = schemas;
        this.runtime = runtime;
        this.credentials = credentials;
        this.json = json;
        this.advertisedHost = advertisedHost;
    }
```

`create(...)`:
```java
    @Transactional
    public DataSource create(String projectId, String name, String protocol, String basis,
            Integer simulatorPort, String realDeviceEndpoint, String runtimeConfig,
            ConnectionCredentials connectionCredentials, List<SchemaNode> initialNodes, String actor) {
        requireProject(projectId);
        Protocol protocolEnum = Protocol.valueOf(protocol);
        SourceBasis.valueOf(basis);
        requireValidJson(runtimeConfig, "runtimeConfig");
        int port = resolvePort(simulatorPort, protocolEnum);
        DataSourceRow row = dataSources.insert(
                projectId, name, protocol, basis, port, realDeviceEndpoint, runtimeConfig, actor);
        applyCredentials(row.id(), connectionCredentials);
        if (initialNodes != null && !initialNodes.isEmpty()) {
            schemas.saveNewVersion(row.id(), initialNodes);
            return map(dataSources.findById(row.id())
                    .orElseThrow(() -> new ResourceNotFoundException("DataSource", row.id())));
        }
        return map(row);
    }
```

`update(...)` — the port is optional (keep existing when null):
```java
    public DataSource update(String projectId, String id, String name, Integer simulatorPort,
            String realDeviceEndpoint, String runtimeConfig, Boolean enabled,
            ConnectionCredentials connectionCredentials, long expectedVersion) {
        DataSourceRow existing = requireRow(projectId, id);
        requireValidJson(runtimeConfig, "runtimeConfig");
        String newName = name != null ? name : existing.name();
        int newPort = simulatorPort != null
                ? requireValidPort(simulatorPort)
                : existing.simulatorPort();
        String newEndpoint = realDeviceEndpoint != null ? realDeviceEndpoint : existing.realDeviceEndpoint();
        String newRuntimeConfig = runtimeConfig != null ? runtimeConfig : existing.runtimeConfig();
        boolean newEnabled = enabled != null ? enabled : existing.enabled();
        DataSourceRow updated = dataSources.update(
                        id, newName, newPort, newEndpoint, newRuntimeConfig, newEnabled, expectedVersion)
                .orElseThrow(() -> new ConcurrencyConflictException("DataSource", id, expectedVersion));
        applyCredentials(id, connectionCredentials);
        return map(updated);
    }
```

`start(...)` — uniqueness on the column:
```java
    public DataSource start(String projectId, String id) {
        DataSourceRow row = requireRow(projectId, id);
        int port = row.simulatorPort();
        for (DataSourceRow other : dataSources.findAll()) {
            if (!other.id().equals(id)
                    && "RUNNING".equals(runtime.state(other.id()))
                    && other.simulatorPort() == port) {
                throw new PortInUseException(port, other.id());
            }
        }
        runtime.start(id, RuntimeStartSpecs.of(schemas, row));
        return map(row);
    }
```

Add the port helpers:
```java
    private static int resolvePort(Integer requested, Protocol protocol) {
        return requested != null ? requireValidPort(requested) : SimulatorUrl.defaultPort(protocol);
    }

    private static int requireValidPort(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("simulatorPort must be in 1..65535");
        }
        return port;
    }
```

`map(...)` — set the new fields + `serveUrl`:
```java
    private DataSource map(DataSourceRow r) {
        Protocol protocol = Protocol.valueOf(r.protocol());
        return new DataSource(
                r.id(),
                r.projectId(),
                r.name(),
                protocol,
                SourceBasis.valueOf(r.basis()),
                r.schemaId(),
                r.schemaVersion(),
                r.simulatorPort(),
                r.realDeviceEndpoint(),
                r.runtimeConfig(),
                r.enabled(),
                RuntimeState.valueOf(runtime.state(r.id())),
                credentials.has(r.id()) ? CredentialState.SESSION_ONLY : CredentialState.MISSING,
                SimulatorUrl.of(protocol, advertisedHost, r.simulatorPort()),
                r.createdAt().toInstant(),
                r.updatedAt().toInstant(),
                r.createdBy(),
                r.version());
    }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :domain:test --tests '*DataSourceServiceTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add domain/src
git commit -m "feat(IS-127): DataSourceService — simulatorPort default+validation, serveUrl, uniqueness on column

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: `RecordingService` reads `realDeviceEndpoint`

**Files:**
- Modify: `domain/.../recording/RecordingService.java`
- Test: the existing `RecordingServiceTest` (adjust the "no endpoint" assertion).

**Interfaces:**
- Consumes: `DataSourceRow.realDeviceEndpoint()`.

- [ ] **Step 1: Update `startCapture`**

Change the guard and the `CaptureSpec` argument:
```java
        if (source.realDeviceEndpoint() == null || source.realDeviceEndpoint().isBlank()) {
            throw new IllegalArgumentException("data source has no real-device endpoint to capture from");
        }
        ...
            CaptureSpec spec = new CaptureSpec(source.protocol(), source.realDeviceEndpoint(),
                    credentials.find(dsId).orElse(null), schema.version(), schema.nodes());
```

- [ ] **Step 2: Update the corresponding test**

If `RecordingServiceTest` builds a `DataSourceRow` or asserts the old message, update it to the new component order and the message `"data source has no real-device endpoint to capture from"`. Run:

Run: `./gradlew :domain:test --tests '*RecordingServiceTest'`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add domain/src
git commit -m "feat(IS-127): capture reads realDeviceEndpoint

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: Scan / duplicate-project / export / import

**Files:**
- Modify: `domain/.../scan/ScanService.java`
- Modify: `domain/.../project/ProjectService.java`
- Modify: `domain/.../io/ProjectZipExporter.java`
- Modify: `domain/.../io/ProjectImportService.java`
- Tests: the existing tests for these services (adjust to new create/insert signatures + fields).

**Interfaces:**
- Consumes: `DataSourceService.create(...)` (Task 5), `DataSourceRepository.insert/update` (Task 2), `DataSourceRow` fields (Task 2).

- [ ] **Step 1: `ScanService.createFromScan`**

The scanned address is the real device. Pass it as `realDeviceEndpoint`, port `null` (default applied). Line ~162 becomes:
```java
                projectId, name, job.protocol(), "SCAN", null, endpoint, null, null, null, actor);
```
(New `create` order: `..., basis, simulatorPort=null, realDeviceEndpoint=endpoint, runtimeConfig=null, credentials=null, initialNodes=null, actor`.)

- [ ] **Step 2: `ProjectService` duplicate-project copy**

Line ~113 currently passes `ds.endpoint(), ds.runtimeConfig(), ds.createdBy()` to `dataSources.insert(...)`. Update to the new repository `insert` signature:
```java
                    ds.simulatorPort(), ds.realDeviceEndpoint(), ds.runtimeConfig(), ds.createdBy());
```
(Confirm the surrounding `insert(...)` argument list matches Task 2's signature: `projectId, name, protocol, basis, simulatorPort, realDeviceEndpoint, runtimeConfigJson, createdBy`.)

- [ ] **Step 3: `ProjectZipExporter`**

Replace the single endpoint entry (line ~131) with both fields:
```java
        m.put("simulatorPort", ds.simulatorPort());
        m.put("realDeviceEndpoint", ds.realDeviceEndpoint());
```
Update the class Javadoc line that says "endpoint only, no credentials" to "serve port + real-device endpoint, no credentials".

- [ ] **Step 4: `ProjectImportService`**

Read the new fields and pass them through create/update (lines ~131-141):
```java
                    Integer simulatorPort = ds.path("simulatorPort").isNumber()
                            ? ds.path("simulatorPort").asInt() : null;
                    String realDeviceEndpoint = ds.path("realDeviceEndpoint").asString(null);
                    ...
                            newProject.id(), dsName, protocol, basis, simulatorPort, realDeviceEndpoint,
                            runtimeConfig, actor);
                    ...
                        dataSources.update(row.id(), row.name(), row.simulatorPort(),
                                row.realDeviceEndpoint(), row.runtimeConfig(), ...);
```
(Match the exact `create`/`update` signatures from Tasks 5/2. Old archives without `simulatorPort` → `null` → default applied. Old `endpoint` key is ignored; if you want to preserve legacy archives' real-device address, also read `ds.path("endpoint").asString(null)` as a fallback for `realDeviceEndpoint`.)

- [ ] **Step 5: Run the domain module tests**

Run: `./gradlew :domain:test`
Expected: PASS (all domain tests, including scan/import/export/project tests updated to the new signatures).

- [ ] **Step 6: Commit**

```bash
git add domain/src
git commit -m "feat(IS-127): scan/import/export/duplicate carry simulatorPort + realDeviceEndpoint

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: API DTOs + controller + config

**Files:**
- Modify: `api/.../datasource/DataSourceController.java`
- Modify: `app/src/main/resources/application.yml`
- Test: the existing web-layer test for the controller.

**Interfaces:**
- Consumes: `DataSourceService.create/update` (Task 5), `DataSource` fields incl. `serveUrl` (Task 5).
- Produces: `CreateDataSourceRequest{ name, protocol, basis, Integer simulatorPort, String realDeviceEndpoint, String runtimeConfig, ConnectionConfigRequest connectionConfig, List<NodeDto> initialSchema }`, `UpdateDataSourceRequest{ name, Integer simulatorPort, String realDeviceEndpoint, runtimeConfig, enabled, connectionConfig }`, `DataSourceResponse{ ..., int simulatorPort, String realDeviceEndpoint, String serveUrl, ... }`.

- [ ] **Step 1: Add the config property**

`app/src/main/resources/application.yml` — under `iotsim:`, add:
```yaml
  # Advertised host used to build a simulated source's client connect URL
  # (serveUrl). Fixed per network/deploy. The worker's bind address is separate
  # (loopback today; external bind is a follow-up). See IS-127.
  simulator:
    advertised-host: ${IOTSIM_SIMULATOR_HOST:localhost}
```

- [ ] **Step 2: Update the DTOs**

```java
    public record CreateDataSourceRequest(
            String name, String protocol, String basis, Integer simulatorPort,
            String realDeviceEndpoint, String runtimeConfig,
            ConnectionConfigRequest connectionConfig, List<NodeDto> initialSchema) {}

    public record UpdateDataSourceRequest(
            String name, Integer simulatorPort, String realDeviceEndpoint, String runtimeConfig,
            Boolean enabled, ConnectionConfigRequest connectionConfig) {}

    public record DataSourceResponse(
            String id, String projectId, String name, String protocol, String basis,
            String schemaId, Integer schemaVersion, int simulatorPort, String realDeviceEndpoint,
            String runtimeConfig, boolean enabled, String runtimeState, String credentialState,
            String serveUrl, Instant createdAt, Instant updatedAt, String createdBy, long version) {

        public static DataSourceResponse from(DataSource d) {
            return new DataSourceResponse(
                    d.id(), d.projectId(), d.name(), d.protocol().name(), d.basis().name(),
                    d.schemaId(), d.schemaVersion(), d.simulatorPort(), d.realDeviceEndpoint(),
                    d.runtimeConfig(), d.enabled(), d.runtimeState().name(), d.credentialState().name(),
                    d.serveUrl(), d.createdAt(), d.updatedAt(), d.createdBy(), d.version());
        }
    }
```

- [ ] **Step 3: Update the create/update controller calls**

`create(...)`:
```java
        DataSource ds = dataSources.create(
                projectId, req.name(), req.protocol(), req.basis(),
                req.simulatorPort(), req.realDeviceEndpoint(), req.runtimeConfig(),
                CredentialRequests.toCredentials(req.connectionConfig()), initialNodes, "local");
```

`update(...)`:
```java
        DataSource ds = dataSources.update(
                projectId, id, req.name(), req.simulatorPort(), req.realDeviceEndpoint(),
                req.runtimeConfig(), req.enabled(),
                CredentialRequests.toCredentials(req.connectionConfig()), parseVersion(ifMatch));
```

- [ ] **Step 4: Update the controller/web test**

In the controller test, change request JSON to send `simulatorPort` (e.g. `4840`) instead of `endpoint`, and assert the response body contains `simulatorPort`, `serveUrl` (e.g. `opc.tcp://localhost:4840/iotsim`) and no `endpoint`. Also add an out-of-range port case asserting **400**, and a same-port-running case asserting **409** (if the test harness supports start).

Run: `./gradlew :api:test --tests '*DataSource*'`
Expected: PASS.

- [ ] **Step 5: Full build (Checkstyle/Spotless/ArchUnit)**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. Fix any Spotless import-order / Checkstyle unused-import violations surfaced only by the full build.

- [ ] **Step 6: Commit**

```bash
git add api/src app/src
git commit -m "feat(IS-127): API DTOs — simulatorPort + realDeviceEndpoint + derived serveUrl; advertised-host config

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review (completed against the spec)

- **Spec coverage:** split fields (Tasks 1,2,5,8) ✓; listenPort out of runtimeConfig (Task 1,4) ✓; serveUrl derived + advertised-host (Tasks 3,5,8) ✓; uniqueness on the column (Task 5) ✓; per-protocol default + range validation (Tasks 3,5) ✓; SYNTHETIC endpoint null backfill (Task 1) ✓; capture rename (Task 6) ✓; import/export/duplicate/scan (Task 7) ✓. **Bind-failure surfacing → companion plan** (out of scope here, tracked).
- **Placeholder scan:** no TBD/TODO; every code step carries real code. The `ReplayService`/`ProjectImportService` edits say "match the exact local names" because those few lines depend on surrounding code the executor sees — the exact target lines and replacement code are given.
- **Type consistency:** `simulatorPort` is `int` on `DataSourceRow`/`DataSource`/repository, `Integer` (nullable) only on the request DTOs and service `create/update` params (null → default). `realDeviceEndpoint` is `String` (nullable) everywhere. `serveUrl` is `String`, computed only in `DataSourceService.map()`. Component order for `DataSourceRow` and `DataSource` is fixed in Tasks 2 and 5 and reused consistently.
