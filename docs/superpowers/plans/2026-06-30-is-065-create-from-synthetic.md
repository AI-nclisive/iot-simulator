# IS-065 — Create from Synthetic Setup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a user create a `basis=SYNTHETIC` data source from a synthetic setup and run it so it emits a bounded, deterministic series of generated values.

**Architecture:** Two service flows that mirror existing twins. Create-from-synthetic mirrors `ScanService.createFromScan` (build schema from the setup, store the serialized config in `runtimeConfig`). Run-synthetic mirrors `ReplayService.replay` (Model A: generate a bounded batch and apply it in one `runtime.applyValues` call, opening a `SYNTHETIC` run + evidence). The continuous live scheduler (Model B) is deferred to a new low-priority task IS-119.

**Tech Stack:** Java 25, Spring Boot, Gradle multi-module monolith, jOOQ, Jackson 3 (`tools.jackson.*`), JUnit 5 + Mockito + AssertJ. The synthetic engine (`SyntheticPattern`, `SyntheticVariable`, `SyntheticGenerator`) and determinism foundation (`DeterministicSettings` → `DeterminismContext`) already exist (IS-062/063/064).

## Global Constraints

- API version stays `/api/v1` — never create `/api/v2`.
- Jackson is 3.x: import `tools.jackson.*` (not `com.fasterxml.jackson.*`); `JacksonException` is unchecked.
- A `@Component`/`@Service` with more than one constructor must annotate the production constructor with `@Autowired` or the context won't boot.
- No DB migration: `data_sources.basis` already allows `SYNTHETIC` (V1); `runs.kind` already allows `SYNTHETIC` (V3).
- Run `./gradlew build` and confirm green before reporting work done.
- Add/update tests for every change. TDD: failing test first.

## Module / package layout

- `domain` module, package `com.ainclusive.iotsim.domain.synthetic` (alongside the existing engine): `SyntheticConfig`, `SyntheticVariableConfig`, `PatternSpec`, `SyntheticConfigMapper`, `SyntheticSourceService`, `SyntheticRunService`, `SyntheticRunSummary`.
- `api` module, new package `com.ainclusive.iotsim.api.synthetic`: `SyntheticSourceController`, `SyntheticRunController`.
- Tests: service tests in `domain/src/test/.../synthetic`; controller tests in `app/src/test/.../app` (where `DataSourceControllerTest`/`ReplayControllerTest` live).

## Reference signatures (already in the codebase — consume, don't redefine)

- `DataSourceService.create(String projectId, String name, String protocol, String basis, String endpoint, String runtimeConfig, ConnectionCredentials connectionCredentials, String actor) -> DataSource` (pass `null` credentials).
- `DataSourceService.get(String projectId, String id) -> DataSource`.
- `SchemaService.save(String projectId, String dataSourceId, List<SchemaNode> nodes) -> Schema`.
- `SchemaNode(String nodeId, String parentId, String path, String name, NodeKind kind, DataType dataType, ValueRank valueRank, Access access, String unit, String description)` — VARIABLE requires non-null dataType/valueRank/access.
- `DataSourceRow` accessors used here: `.basis()`, `.runtimeConfig()`, `.projectId()`, `.id()`.
- `DataSourceRepository.findById(String) -> Optional<DataSourceRow>`.
- `RuntimeController.start(String dataSourceId, RuntimeStartSpec) -> String`, `.applyValues(String dataSourceId, List<NeutralValue>) -> long`.
- `RuntimeStartSpecs.of(SchemaRepository schemas, DataSourceRow source) -> RuntimeStartSpec`.
- `RunRepository.create(projectId, kind, trigger, initiator, List<String> sourceIds, scenarioId)`, `.start(id, OffsetDateTime)`, `.end(id, terminalState, OffsetDateTime)`, `.linkEvidence(runId, evidenceId)`.
- `EvidenceRepository.create(projectId, runId, createdBy) -> EvidenceRow`, `.updateManifest(id, manifestJson) -> EvidenceRow`.
- `DeterministicSettings(long seed, Instant startTime)`, `DeterministicSettings.withRandomSeed(Instant) `, `.seed()`, `.newContext() -> DeterminismContext`.
- `SyntheticVariable(String nodeId, DataType dataType, SyntheticPattern pattern, long updateRateMs)`, `.generator(DeterminismContext) -> SyntheticGenerator`, `.updateRateMs()`.
- `SyntheticGenerator.next() -> NeutralValue`.
- `NeutralValue.sourceTime() -> Instant`, `.nodeId() -> String`.
- Domain pattern records: `SyntheticPattern.Constant(double value)`, `.Ramp(double min,double max,Duration period)`, `.Sine(...)`, `.Square(...)`, `.RandomUniform(double min,double max)`, `.RandomWalk(double min,double max,double volatility)`, `.EnumCycle(List<Object> values)`, `.StepSequence(List<StepSequence.Step> steps)`, `StepSequence.Step(Object value, Duration hold)`.
- `DataSourceController.DataSourceResponse.from(DataSource) -> DataSourceResponse` (reuse for responses).

---

### Task 1: Serialized synthetic config + mapper

**Files:**
- Create: `domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/SyntheticConfig.java`
- Create: `domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/SyntheticVariableConfig.java`
- Create: `domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/PatternSpec.java`
- Create: `domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/SyntheticConfigMapper.java`
- Test: `domain/src/test/java/com/ainclusive/iotsim/domain/synthetic/SyntheticConfigMapperTest.java`

**Interfaces:**
- Produces: `SyntheticConfig(Long seed, List<SyntheticVariableConfig> variables)`; `SyntheticVariableConfig(String nodeId, DataType dataType, PatternSpec pattern, long updateRateMs)`; `PatternSpec(String type, Double value, Double min, Double max, Long periodMs, Double volatility, List<Object> values, List<PatternSpec.StepSpec> steps)` with nested `PatternSpec.StepSpec(Object value, long holdMs)`; `SyntheticConfigMapper.toVariables(SyntheticConfig) -> List<SyntheticVariable>` and `SyntheticConfigMapper.toPattern(PatternSpec) -> SyntheticPattern`.
- Consumes: domain `SyntheticPattern`, `SyntheticVariable`, `DataType`.

- [ ] **Step 1: Write the failing test**

`domain/src/test/java/com/ainclusive/iotsim/domain/synthetic/SyntheticConfigMapperTest.java`:

```java
package com.ainclusive.iotsim.domain.synthetic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.protocolmodel.DataType;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SyntheticConfigMapperTest {

    private static PatternSpec spec(String type, Double value, Double min, Double max,
            Long periodMs, Double volatility, List<Object> values, List<PatternSpec.StepSpec> steps) {
        return new PatternSpec(type, value, min, max, periodMs, volatility, values, steps);
    }

    @Test
    void mapsConstant() {
        var p = SyntheticConfigMapper.toPattern(spec("CONSTANT", 5.0, null, null, null, null, null, null));
        assertThat(p).isEqualTo(new SyntheticPattern.Constant(5.0));
    }

    @Test
    void mapsSineWithPeriodMillis() {
        var p = SyntheticConfigMapper.toPattern(spec("SINE", null, 0.0, 10.0, 2000L, null, null, null));
        assertThat(p).isEqualTo(new SyntheticPattern.Sine(0.0, 10.0, Duration.ofMillis(2000)));
    }

    @Test
    void mapsStepSequenceWithHoldMillis() {
        var p = SyntheticConfigMapper.toPattern(spec("STEP_SEQUENCE", null, null, null, null, null, null,
                List.of(new PatternSpec.StepSpec("a", 1000L), new PatternSpec.StepSpec("b", 2000L))));
        assertThat(p).isEqualTo(new SyntheticPattern.StepSequence(List.of(
                new SyntheticPattern.StepSequence.Step("a", Duration.ofMillis(1000)),
                new SyntheticPattern.StepSequence.Step("b", Duration.ofMillis(2000)))));
    }

    @Test
    void unknownTypeRejected() {
        assertThatThrownBy(() -> SyntheticConfigMapper.toPattern(
                spec("WOBBLE", null, null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("WOBBLE");
    }

    @Test
    void missingRequiredParamRejected() {
        assertThatThrownBy(() -> SyntheticConfigMapper.toPattern(
                spec("RAMP", null, 0.0, 10.0, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("periodMs");
    }

    @Test
    void toVariablesMapsEachVariable() {
        var config = new SyntheticConfig(7L, List.of(
                new SyntheticVariableConfig("n1", DataType.FLOAT64,
                        spec("CONSTANT", 1.0, null, null, null, null, null, null), 500)));
        List<SyntheticVariable> vars = SyntheticConfigMapper.toVariables(config);
        assertThat(vars).containsExactly(
                new SyntheticVariable("n1", DataType.FLOAT64, new SyntheticPattern.Constant(1.0), 500));
    }

    @Test
    void emptyVariablesRejected() {
        assertThatThrownBy(() -> new SyntheticConfig(1L, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one variable");
    }

    @Test
    void duplicateNodeIdRejected() {
        var v = new SyntheticVariableConfig("dup", DataType.INT32,
                spec("CONSTANT", 1.0, null, null, null, null, null, null), 100);
        assertThatThrownBy(() -> new SyntheticConfig(1L, List.of(v, v)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dup");
    }

    @Test
    void jsonRoundTripPreservesConfig() {
        ObjectMapper json = new ObjectMapper();
        var config = new SyntheticConfig(42L, List.of(
                new SyntheticVariableConfig("temp", DataType.FLOAT64,
                        spec("SINE", null, 0.0, 100.0, 1000L, null, null, null), 250)));
        String text = json.writeValueAsString(config);
        SyntheticConfig back = json.readValue(text, SyntheticConfig.class);
        assertThat(back).isEqualTo(config);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :domain:test --tests "com.ainclusive.iotsim.domain.synthetic.SyntheticConfigMapperTest"`
Expected: FAIL — compilation error, `SyntheticConfig`/`PatternSpec`/`SyntheticConfigMapper` do not exist.

- [ ] **Step 3: Write `SyntheticVariableConfig`**

`domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/SyntheticVariableConfig.java`:

```java
package com.ainclusive.iotsim.domain.synthetic;

import com.ainclusive.iotsim.protocolmodel.DataType;

/**
 * One variable's synthetic binding inside a {@link SyntheticConfig}: the target
 * node, its neutral type, the pattern to generate, and the sample interval.
 * (backend-specs/06 "Synthetic generation model".)
 */
public record SyntheticVariableConfig(String nodeId, DataType dataType, PatternSpec pattern, long updateRateMs) {}
```

- [ ] **Step 4: Write `PatternSpec`**

`domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/PatternSpec.java`:

```java
package com.ainclusive.iotsim.domain.synthetic;

import java.util.List;

/**
 * Serialized form of a {@link SyntheticPattern}: a {@code type} discriminator plus
 * the union of pattern parameters (only those relevant to the type are set).
 * Durations are milliseconds. {@link SyntheticConfigMapper} maps this to the domain
 * pattern. (backend-specs/06 "Synthetic generation model".)
 */
public record PatternSpec(
        String type,
        Double value,
        Double min,
        Double max,
        Long periodMs,
        Double volatility,
        List<Object> values,
        List<StepSpec> steps) {

    /** One step of a STEP_SEQUENCE: a value held for {@code holdMs} milliseconds. */
    public record StepSpec(Object value, long holdMs) {}
}
```

- [ ] **Step 5: Write `SyntheticConfig`**

`domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/SyntheticConfig.java`:

```java
package com.ainclusive.iotsim.domain.synthetic;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The serialized synthetic-generation setup stored in {@code DataSource.runtimeConfig}
 * — a master {@code seed} ({@code null} = run picks one) plus the per-variable
 * bindings. (backend-specs/06 "Synthetic generation model".)
 */
public record SyntheticConfig(Long seed, List<SyntheticVariableConfig> variables) {

    public SyntheticConfig {
        if (variables == null || variables.isEmpty()) {
            throw new IllegalArgumentException("synthetic config requires at least one variable");
        }
        variables = List.copyOf(variables);
        Set<String> ids = new HashSet<>();
        for (SyntheticVariableConfig v : variables) {
            if (!ids.add(v.nodeId())) {
                throw new IllegalArgumentException("duplicate synthetic variable nodeId: " + v.nodeId());
            }
        }
    }
}
```

- [ ] **Step 6: Write `SyntheticConfigMapper`**

`domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/SyntheticConfigMapper.java`:

```java
package com.ainclusive.iotsim.domain.synthetic;

import com.ainclusive.iotsim.domain.synthetic.SyntheticPattern.StepSequence.Step;
import java.time.Duration;
import java.util.List;

/** Maps the serialized {@link SyntheticConfig} to runnable domain {@link SyntheticVariable}s. */
public final class SyntheticConfigMapper {

    private SyntheticConfigMapper() {}

    public static List<SyntheticVariable> toVariables(SyntheticConfig config) {
        return config.variables().stream()
                .map(v -> new SyntheticVariable(v.nodeId(), v.dataType(), toPattern(v.pattern()), v.updateRateMs()))
                .toList();
    }

    public static SyntheticPattern toPattern(PatternSpec spec) {
        if (spec == null || spec.type() == null) {
            throw new IllegalArgumentException("pattern type is required");
        }
        return switch (spec.type()) {
            case "CONSTANT" -> new SyntheticPattern.Constant(req(spec.value(), "value"));
            case "RAMP" -> new SyntheticPattern.Ramp(req(spec.min(), "min"), req(spec.max(), "max"), period(spec));
            case "SINE" -> new SyntheticPattern.Sine(req(spec.min(), "min"), req(spec.max(), "max"), period(spec));
            case "SQUARE" -> new SyntheticPattern.Square(req(spec.min(), "min"), req(spec.max(), "max"), period(spec));
            case "RANDOM_UNIFORM" ->
                    new SyntheticPattern.RandomUniform(req(spec.min(), "min"), req(spec.max(), "max"));
            case "RANDOM_WALK" -> new SyntheticPattern.RandomWalk(
                    req(spec.min(), "min"), req(spec.max(), "max"), req(spec.volatility(), "volatility"));
            case "ENUM_CYCLE" -> new SyntheticPattern.EnumCycle(reqList(spec.values(), "values"));
            case "STEP_SEQUENCE" -> new SyntheticPattern.StepSequence(steps(spec));
            default -> throw new IllegalArgumentException("unknown pattern type: " + spec.type());
        };
    }

    private static List<Step> steps(PatternSpec spec) {
        return reqList(spec.steps(), "steps").stream()
                .map(s -> new Step(s.value(), Duration.ofMillis(s.holdMs())))
                .toList();
    }

    private static Duration period(PatternSpec spec) {
        return Duration.ofMillis(req(spec.periodMs(), "periodMs"));
    }

    private static double req(Double v, String name) {
        if (v == null) {
            throw new IllegalArgumentException(name + " is required for this pattern");
        }
        return v;
    }

    private static long req(Long v, String name) {
        if (v == null) {
            throw new IllegalArgumentException(name + " is required for this pattern");
        }
        return v;
    }

    private static <T> List<T> reqList(List<T> v, String name) {
        if (v == null || v.isEmpty()) {
            throw new IllegalArgumentException(name + " is required and must be non-empty for this pattern");
        }
        return v;
    }
}
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `./gradlew :domain:test --tests "com.ainclusive.iotsim.domain.synthetic.SyntheticConfigMapperTest"`
Expected: PASS (all cases green).

- [ ] **Step 8: Commit**

```bash
git add domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/SyntheticConfig.java \
        domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/SyntheticVariableConfig.java \
        domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/PatternSpec.java \
        domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/SyntheticConfigMapper.java \
        domain/src/test/java/com/ainclusive/iotsim/domain/synthetic/SyntheticConfigMapperTest.java
git commit -m "feat(gen): IS-065 serialized synthetic config + domain mapper"
```

---

### Task 2: Create-from-synthetic service

**Files:**
- Create: `domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/SyntheticSourceService.java`
- Test: `domain/src/test/java/com/ainclusive/iotsim/domain/synthetic/SyntheticSourceServiceTest.java`

**Interfaces:**
- Consumes: Task 1 types; `DataSourceService.create/get`; `SchemaService.save`; `tools.jackson.databind.ObjectMapper`.
- Produces: `SyntheticSourceService.create(String projectId, String name, String protocol, String endpoint, SyntheticConfig config, String actor) -> DataSource`.

- [ ] **Step 1: Write the failing test**

`domain/src/test/java/com/ainclusive/iotsim/domain/synthetic/SyntheticSourceServiceTest.java`:

```java
package com.ainclusive.iotsim.domain.synthetic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ainclusive.iotsim.domain.datasource.DataSource;
import com.ainclusive.iotsim.domain.datasource.DataSourceService;
import com.ainclusive.iotsim.domain.datasource.Protocol;
import com.ainclusive.iotsim.domain.datasource.RuntimeState;
import com.ainclusive.iotsim.domain.datasource.SourceBasis;
import com.ainclusive.iotsim.domain.datasource.CredentialState;
import com.ainclusive.iotsim.domain.schema.SchemaService;
import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class SyntheticSourceServiceTest {

    private static final String PROJECT = "p1";

    private DataSourceService dataSources;
    private SchemaService schemas;
    private SyntheticSourceService service;

    @BeforeEach
    void setUp() {
        dataSources = mock(DataSourceService.class);
        schemas = mock(SchemaService.class);
        service = new SyntheticSourceService(dataSources, schemas, new ObjectMapper());
    }

    private static SyntheticConfig config() {
        return new SyntheticConfig(9L, List.of(
                new SyntheticVariableConfig("temp", DataType.FLOAT64,
                        new PatternSpec("SINE", null, 0.0, 10.0, 1000L, null, null, null), 250),
                new SyntheticVariableConfig("level", DataType.INT32,
                        new PatternSpec("CONSTANT", 3.0, null, null, null, null, null, null), 500)));
    }

    private static DataSource sample(String id) {
        Instant now = Instant.now();
        return new DataSource(id, PROJECT, "Gen", Protocol.OPC_UA, SourceBasis.SYNTHETIC,
                null, null, "{}", "{}", false, RuntimeState.STOPPED, CredentialState.MISSING,
                now, now, "local", 0);
    }

    @Test
    void createStoresSyntheticBasisAndSerializedConfigAndBuildsSchema() {
        given(dataSources.create(eq(PROJECT), eq("Gen"), eq("OPC_UA"), eq("SYNTHETIC"),
                any(), any(), any(), eq("local"))).willReturn(sample("ds1"));
        given(dataSources.get(PROJECT, "ds1")).willReturn(sample("ds1"));

        DataSource result = service.create(PROJECT, "Gen", "OPC_UA", "{}", config(), "local");

        assertThat(result.id()).isEqualTo("ds1");

        ArgumentCaptor<String> runtimeConfig = ArgumentCaptor.forClass(String.class);
        verify(dataSources).create(eq(PROJECT), eq("Gen"), eq("OPC_UA"), eq("SYNTHETIC"),
                eq("{}"), runtimeConfig.capture(), any(), eq("local"));
        assertThat(runtimeConfig.getValue()).contains("\"seed\":9").contains("temp").contains("level");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SchemaNode>> nodes = ArgumentCaptor.forClass(List.class);
        verify(schemas).save(eq(PROJECT), eq("ds1"), nodes.capture());
        assertThat(nodes.getValue()).hasSize(2);
        SchemaNode first = nodes.getValue().get(0);
        assertThat(first.nodeId()).isEqualTo("temp");
        assertThat(first.kind()).isEqualTo(NodeKind.VARIABLE);
        assertThat(first.dataType()).isEqualTo(DataType.FLOAT64);
    }

    @Test
    void createWithNullConfigRejected() {
        assertThatThrownBy(() -> service.create(PROJECT, "Gen", "OPC_UA", "{}", null, "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("config is required");
    }

    @Test
    void createWithInvalidPatternRejectedBeforeAnyWrite() {
        var bad = new SyntheticConfig(1L, List.of(
                new SyntheticVariableConfig("x", DataType.FLOAT64,
                        new PatternSpec("RAMP", null, 0.0, 10.0, null, null, null, null), 100)));
        assertThatThrownBy(() -> service.create(PROJECT, "Gen", "OPC_UA", "{}", bad, "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("periodMs");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :domain:test --tests "com.ainclusive.iotsim.domain.synthetic.SyntheticSourceServiceTest"`
Expected: FAIL — `SyntheticSourceService` does not exist.

- [ ] **Step 3: Write `SyntheticSourceService`**

`domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/SyntheticSourceService.java`:

```java
package com.ainclusive.iotsim.domain.synthetic;

import com.ainclusive.iotsim.domain.datasource.DataSource;
import com.ainclusive.iotsim.domain.datasource.DataSourceService;
import com.ainclusive.iotsim.domain.schema.SchemaService;
import com.ainclusive.iotsim.protocolmodel.Access;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import com.ainclusive.iotsim.protocolmodel.ValueRank;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Create-from-synthetic (IS-065): builds a {@code SYNTHETIC} data source from a
 * synthetic setup — derives the schema from the variables and stores the serialized
 * config in {@code runtimeConfig}. The generated twin of {@code ScanService.createFromScan}
 * (SPEC "Generate Synthetic Data" / "Manually Create Data Source Schemas").
 */
@Service
public class SyntheticSourceService {

    private final DataSourceService dataSources;
    private final SchemaService schemas;
    private final ObjectMapper json;

    public SyntheticSourceService(DataSourceService dataSources, SchemaService schemas, ObjectMapper json) {
        this.dataSources = dataSources;
        this.schemas = schemas;
        this.json = json;
    }

    public DataSource create(String projectId, String name, String protocol, String endpoint,
            SyntheticConfig config, String actor) {
        if (config == null) {
            throw new IllegalArgumentException("config is required");
        }
        // Validate the whole config up front (patterns, types, rates) before any write.
        SyntheticConfigMapper.toVariables(config);
        List<SchemaNode> nodes = schemaNodes(config);
        DataSource created = dataSources.create(
                projectId, name, protocol, "SYNTHETIC", endpoint, json.writeValueAsString(config), null, actor);
        schemas.save(projectId, created.id(), nodes);
        // Re-read so the response carries the linked schemaId/schemaVersion.
        return dataSources.get(projectId, created.id());
    }

    /** One VARIABLE node per synthetic variable; path derived from the (unique) nodeId. */
    private static List<SchemaNode> schemaNodes(SyntheticConfig config) {
        List<SchemaNode> nodes = new ArrayList<>();
        for (SyntheticVariableConfig v : config.variables()) {
            nodes.add(new SchemaNode(v.nodeId(), null, "/" + v.nodeId(), v.nodeId(),
                    NodeKind.VARIABLE, v.dataType(), ValueRank.SCALAR, Access.READ, null, null));
        }
        return nodes;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :domain:test --tests "com.ainclusive.iotsim.domain.synthetic.SyntheticSourceServiceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/SyntheticSourceService.java \
        domain/src/test/java/com/ainclusive/iotsim/domain/synthetic/SyntheticSourceServiceTest.java
git commit -m "feat(gen): IS-065 create-from-synthetic service"
```

---

### Task 3: Run-synthetic service (Model A feed)

**Files:**
- Create: `domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/SyntheticRunSummary.java`
- Create: `domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/SyntheticRunService.java`
- Test: `domain/src/test/java/com/ainclusive/iotsim/domain/synthetic/SyntheticRunServiceTest.java`

**Interfaces:**
- Consumes: Task 1 types; `DataSourceRepository`, `SchemaRepository`, `RuntimeController`, `RunRepository`, `EvidenceRepository`, `ObjectMapper`, `RuntimeStartSpecs.of`, `DeterministicSettings`, `SyntheticGenerator`.
- Produces: `SyntheticRunSummary(String dataSourceId, long valueCount, long seed, String runId, String evidenceId)`; `SyntheticRunService.run(String projectId, String dataSourceId, long durationMs) -> SyntheticRunSummary`.

- [ ] **Step 1: Write the failing test**

`domain/src/test/java/com/ainclusive/iotsim/domain/synthetic/SyntheticRunServiceTest.java`:

```java
package com.ainclusive.iotsim.domain.synthetic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.evidence.EvidenceRepository;
import com.ainclusive.iotsim.persistence.evidence.EvidenceRow;
import com.ainclusive.iotsim.persistence.run.RunRepository;
import com.ainclusive.iotsim.persistence.run.RunRow;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaWithNodes;
import com.ainclusive.iotsim.platform.runtime.RuntimeController;
import com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec;
import com.ainclusive.iotsim.platform.runtime.SourceHealth;
import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SyntheticRunServiceTest {

    private static final String PROJECT = "p1";
    private static final String SOURCE = "ds1";
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private final ObjectMapper json = new ObjectMapper();
    private CapturingRuntime runtime;
    private FakeRuns runs;
    private FakeEvidence evidence;

    @BeforeEach
    void setUp() {
        runtime = new CapturingRuntime();
        runs = new FakeRuns();
        evidence = new FakeEvidence();
    }

    private SyntheticConfig config(Long seed) {
        return new SyntheticConfig(seed, List.of(
                new SyntheticVariableConfig("temp", DataType.FLOAT64,
                        new PatternSpec("SINE", null, 0.0, 10.0, 1000L, null, null, null), 100),
                new SyntheticVariableConfig("rnd", DataType.FLOAT64,
                        new PatternSpec("RANDOM_UNIFORM", null, 0.0, 1.0, null, null, null, null), 250)));
    }

    private SyntheticRunService service(String basis, SyntheticConfig config) {
        String rc = config == null ? "{}" : json.writeValueAsString(config);
        return new SyntheticRunService(new FakeDataSources(basis, rc), new EmptySchemas(),
                runtime, runs, evidence, json, FIXED);
    }

    @Test
    void appliesBoundedDeterministicSeriesAndOpensRunAndEvidence() {
        SyntheticRunService service = service("SYNTHETIC", config(5L));

        SyntheticRunSummary summary = service.run(PROJECT, SOURCE, 1000);

        // temp: 1000/100 = 10 ticks; rnd: 1000/250 = 4 ticks => 14 values.
        assertThat(summary.valueCount()).isEqualTo(14);
        assertThat(summary.seed()).isEqualTo(5L);
        assertThat(runtime.applied).hasSize(14);
        assertThat(runtime.state(SOURCE)).isEqualTo("RUNNING");

        RunRow run = runs.byId.get(summary.runId());
        assertThat(run.kind()).isEqualTo("SYNTHETIC");
        assertThat(run.state()).isEqualTo("COMPLETED");
        assertThat(run.sourceIds()).containsExactly(SOURCE);
        EvidenceRow ev = evidence.byId.get(summary.evidenceId());
        assertThat(ev.manifestJson()).contains("\"seed\":5");
    }

    @Test
    void sameSeedProducesIdenticalSeries() {
        List<NeutralValue> first = service("SYNTHETIC", config(5L)).run(PROJECT, SOURCE, 1000) != null
                ? new ArrayList<>(runtime.applied) : null;
        CapturingRuntime second = new CapturingRuntime();
        SyntheticRunService svc2 = new SyntheticRunService(
                new FakeDataSources("SYNTHETIC", json.writeValueAsString(config(5L))),
                new EmptySchemas(), second, new FakeRuns(), new FakeEvidence(), json, FIXED);
        svc2.run(PROJECT, SOURCE, 1000);
        assertThat(second.applied).isEqualTo(first);
    }

    @Test
    void nonSyntheticSourceRejected() {
        assertThatThrownBy(() -> service("MANUAL", config(1L)).run(PROJECT, SOURCE, 1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not synthetic");
    }

    @Test
    void nonPositiveDurationRejected() {
        assertThatThrownBy(() -> service("SYNTHETIC", config(1L)).run(PROJECT, SOURCE, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("durationMs");
    }

    @Test
    void missingSourceThrowsNotFound() {
        SyntheticRunService service = new SyntheticRunService(new FakeDataSources("SYNTHETIC", "{}"),
                new EmptySchemas(), runtime, runs, evidence, json, FIXED);
        assertThatThrownBy(() -> service.run(PROJECT, "nope", 1000))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void runtimeFailureEndsRunFailed() {
        SyntheticRunService service = new SyntheticRunService(
                new FakeDataSources("SYNTHETIC", json.writeValueAsString(config(1L))),
                new EmptySchemas(), new ThrowingRuntime(), runs, evidence, json, FIXED);
        assertThatThrownBy(() -> service.run(PROJECT, SOURCE, 1000))
                .isInstanceOf(IllegalStateException.class);
        assertThat(runs.byId.values()).singleElement()
                .extracting(RunRow::state).isEqualTo("FAILED");
    }

    // --- fakes ---

    private static final class CapturingRuntime implements RuntimeController {
        final List<NeutralValue> applied = new ArrayList<>();
        private String state = "STOPPED";

        public String start(String id, RuntimeStartSpec spec) {
            state = "RUNNING";
            return state;
        }

        public String stop(String id) {
            state = "STOPPED";
            return state;
        }

        public String state(String id) {
            return state;
        }

        public long applyValues(String id, List<NeutralValue> values) {
            applied.addAll(values);
            return values.size();
        }

        public SourceHealth health(String id) {
            return new SourceHealth(state, null);
        }
    }

    private static final class ThrowingRuntime implements RuntimeController {
        public String start(String id, RuntimeStartSpec spec) {
            throw new IllegalStateException("worker launch failed");
        }

        public String stop(String id) {
            return "STOPPED";
        }

        public String state(String id) {
            return "STOPPED";
        }

        public long applyValues(String id, List<NeutralValue> values) {
            return 0;
        }

        public SourceHealth health(String id) {
            return new SourceHealth("STOPPED", null);
        }
    }

    private record FakeDataSources(String basis, String runtimeConfig) implements DataSourceRepository {
        public Optional<DataSourceRow> findById(String id) {
            if (!SOURCE.equals(id)) {
                return Optional.empty();
            }
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            return Optional.of(new DataSourceRow(id, PROJECT, "Gen", "OPC_UA", basis,
                    null, null, "{}", runtimeConfig, false, now, now, "local", 0));
        }

        public DataSourceRow insert(String p, String n, String pr, String b, String e, String rc, String c) {
            throw new UnsupportedOperationException();
        }

        public List<DataSourceRow> findByProject(String projectId) {
            return List.of();
        }

        public Optional<DataSourceRow> update(String i, String n, String e, String rc, boolean en, long v) {
            throw new UnsupportedOperationException();
        }

        public boolean deleteById(String id) {
            throw new UnsupportedOperationException();
        }
    }

    private record EmptySchemas() implements SchemaRepository {
        public Optional<SchemaWithNodes> findCurrent(String dataSourceId) {
            return Optional.empty();
        }

        public SchemaWithNodes saveNewVersion(String dataSourceId, List<SchemaNode> nodes) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeRuns implements RunRepository {
        final java.util.Map<String, RunRow> byId = new java.util.LinkedHashMap<>();
        private int seq;

        public RunRow create(String projectId, String kind, String trigger, String initiator,
                List<String> sourceIds, String scenarioId) {
            String id = "run-" + (++seq);
            RunRow row = new RunRow(id, projectId, kind, trigger, initiator, "QUEUED",
                    scenarioId, null, null, null, OffsetDateTime.now(ZoneOffset.UTC), new ArrayList<>(sourceIds));
            byId.put(id, row);
            return row;
        }

        public RunRow start(String id, OffsetDateTime startedAt) {
            RunRow r = byId.get(id);
            RunRow u = new RunRow(r.id(), r.projectId(), r.kind(), r.trigger(), r.initiator(),
                    "RUNNING", r.scenarioId(), r.evidenceId(), startedAt, r.endedAt(), r.createdAt(), r.sourceIds());
            byId.put(id, u);
            return u;
        }

        public RunRow end(String id, String terminalState, OffsetDateTime endedAt) {
            RunRow r = byId.get(id);
            RunRow u = new RunRow(r.id(), r.projectId(), r.kind(), r.trigger(), r.initiator(),
                    terminalState, r.scenarioId(), r.evidenceId(), r.startedAt(), endedAt, r.createdAt(), r.sourceIds());
            byId.put(id, u);
            return u;
        }

        public RunRow linkEvidence(String runId, String evidenceId) {
            RunRow r = byId.get(runId);
            RunRow u = new RunRow(r.id(), r.projectId(), r.kind(), r.trigger(), r.initiator(),
                    r.state(), r.scenarioId(), evidenceId, r.startedAt(), r.endedAt(), r.createdAt(), r.sourceIds());
            byId.put(runId, u);
            return u;
        }

        public Optional<RunRow> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        public List<RunRow> findByProject(String projectId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeEvidence implements EvidenceRepository {
        final java.util.Map<String, EvidenceRow> byId = new java.util.LinkedHashMap<>();
        private int seq;

        public EvidenceRow create(String projectId, String runId, String createdBy) {
            String id = "ev-" + (++seq);
            EvidenceRow row = new EvidenceRow(id, projectId, runId, "CAPTURING", "{}", null,
                    OffsetDateTime.now(ZoneOffset.UTC), createdBy);
            byId.put(id, row);
            return row;
        }

        public EvidenceRow updateManifest(String id, String manifestJson) {
            EvidenceRow e = byId.get(id);
            EvidenceRow u = new EvidenceRow(e.id(), e.projectId(), e.runId(), e.status(),
                    manifestJson, e.objectRef(), e.createdAt(), e.createdBy());
            byId.put(id, u);
            return u;
        }

        public Optional<EvidenceRow> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        public Optional<EvidenceRow> findByRun(String runId) {
            throw new UnsupportedOperationException();
        }

        public List<EvidenceRow> findByProject(String projectId) {
            throw new UnsupportedOperationException();
        }

        public EvidenceRow updateStatus(String id, String status, String objectRef) {
            throw new UnsupportedOperationException();
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :domain:test --tests "com.ainclusive.iotsim.domain.synthetic.SyntheticRunServiceTest"`
Expected: FAIL — `SyntheticRunService` / `SyntheticRunSummary` do not exist.

- [ ] **Step 3: Write `SyntheticRunSummary`**

`domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/SyntheticRunSummary.java`:

```java
package com.ainclusive.iotsim.domain.synthetic;

/**
 * Result of a Model-A synthetic run: how many generated values were applied to the
 * source, the effective (captured) seed, and the run/evidence ids.
 */
public record SyntheticRunSummary(String dataSourceId, long valueCount, long seed, String runId, String evidenceId) {}
```

- [ ] **Step 4: Write `SyntheticRunService`**

`domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/SyntheticRunService.java`:

```java
package com.ainclusive.iotsim.domain.synthetic;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.datasource.RuntimeStartSpecs;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.evidence.EvidenceRepository;
import com.ainclusive.iotsim.persistence.evidence.EvidenceRow;
import com.ainclusive.iotsim.persistence.run.RunRepository;
import com.ainclusive.iotsim.persistence.run.RunRow;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.platform.runtime.RuntimeController;
import com.ainclusive.iotsim.protocolmodel.DeterminismContext;
import com.ainclusive.iotsim.protocolmodel.DeterministicSettings;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Run-synthetic (IS-065, Model A): generates a bounded, deterministic batch from a
 * source's stored {@link SyntheticConfig} and applies it via the runtime in one shot,
 * opening a {@code SYNTHETIC} run + evidence. The generated twin of
 * {@code ReplayService.replay}; consistent with the system's no client-timing
 * guarantee (continuous live pacing is the deferred IS-119 / IS-069).
 */
@Service
public class SyntheticRunService {

    private final DataSourceRepository dataSources;
    private final SchemaRepository schemas;
    private final RuntimeController runtime;
    private final RunRepository runs;
    private final EvidenceRepository evidence;
    private final ObjectMapper json;
    private final Clock clock;

    @Autowired
    public SyntheticRunService(DataSourceRepository dataSources, SchemaRepository schemas,
            RuntimeController runtime, RunRepository runs, EvidenceRepository evidence, ObjectMapper json) {
        this(dataSources, schemas, runtime, runs, evidence, json, Clock.systemUTC());
    }

    /** Test seam: pin the run-start clock so the produced series is fully reproducible. */
    SyntheticRunService(DataSourceRepository dataSources, SchemaRepository schemas,
            RuntimeController runtime, RunRepository runs, EvidenceRepository evidence,
            ObjectMapper json, Clock clock) {
        this.dataSources = dataSources;
        this.schemas = schemas;
        this.runtime = runtime;
        this.runs = runs;
        this.evidence = evidence;
        this.json = json;
        this.clock = clock;
    }

    public SyntheticRunSummary run(String projectId, String dataSourceId, long durationMs) {
        if (durationMs <= 0) {
            throw new IllegalArgumentException("durationMs must be > 0: " + durationMs);
        }
        DataSourceRow source = requireSource(projectId, dataSourceId);
        if (!"SYNTHETIC".equals(source.basis())) {
            throw new IllegalArgumentException("data source is not synthetic: " + dataSourceId);
        }
        SyntheticConfig config = parseConfig(source.runtimeConfig());
        List<SyntheticVariable> variables = SyntheticConfigMapper.toVariables(config);

        DeterministicSettings settings = config.seed() == null
                ? DeterministicSettings.withRandomSeed(clock.instant())
                : new DeterministicSettings(config.seed(), clock.instant());

        RunRow run = runs.create(projectId, "SYNTHETIC", "MANUAL", "local", List.of(dataSourceId), null);
        try {
            runs.start(run.id(), now());
            EvidenceRow evidenceRow = evidence.create(projectId, run.id(), "local");
            runs.linkEvidence(run.id(), evidenceRow.id());

            List<NeutralValue> values = generate(variables, settings, durationMs);
            evidence.updateManifest(evidenceRow.id(), manifest(settings.seed(), values.size()));

            runtime.start(dataSourceId, RuntimeStartSpecs.of(schemas, source));
            long applied = runtime.applyValues(dataSourceId, values);
            runs.end(run.id(), "COMPLETED", now());
            return new SyntheticRunSummary(dataSourceId, applied, settings.seed(), run.id(), evidenceRow.id());
        } catch (RuntimeException e) {
            runs.end(run.id(), "FAILED", now());
            throw e;
        }
    }

    /**
     * Generates {@code durationMs / updateRateMs} samples per variable from one shared
     * run context (per-node RNG streams keep variables independent yet reproducible),
     * merged into one timeline ordered by sourceTime then nodeId.
     */
    private static List<NeutralValue> generate(
            List<SyntheticVariable> variables, DeterministicSettings settings, long durationMs) {
        DeterminismContext context = settings.newContext();
        List<NeutralValue> values = new ArrayList<>();
        for (SyntheticVariable variable : variables) {
            SyntheticGenerator generator = variable.generator(context);
            long ticks = durationMs / variable.updateRateMs();
            for (long i = 0; i < ticks; i++) {
                values.add(generator.next());
            }
        }
        values.sort(Comparator.comparing(NeutralValue::sourceTime).thenComparing(NeutralValue::nodeId));
        return values;
    }

    /** Inspectable evidence seed: the effective seed + value count (no recordingId for synthetic). */
    private String manifest(long seed, int valueCount) {
        return json.writeValueAsString(Map.of("synthetic", true, "seed", seed, "valueCount", valueCount));
    }

    private SyntheticConfig parseConfig(String runtimeConfig) {
        try {
            return json.readValue(runtimeConfig, SyntheticConfig.class);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("invalid synthetic runtimeConfig: " + e.getMessage(), e);
        }
    }

    private DataSourceRow requireSource(String projectId, String dataSourceId) {
        return dataSources.findById(dataSourceId)
                .filter(r -> r.projectId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("DataSource", dataSourceId));
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :domain:test --tests "com.ainclusive.iotsim.domain.synthetic.SyntheticRunServiceTest"`
Expected: PASS. (If `sameSeedProducesIdenticalSeries` ever flakes, it indicates a determinism regression — investigate, do not loosen the assertion.)

- [ ] **Step 6: Commit**

```bash
git add domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/SyntheticRunSummary.java \
        domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/SyntheticRunService.java \
        domain/src/test/java/com/ainclusive/iotsim/domain/synthetic/SyntheticRunServiceTest.java
git commit -m "feat(gen): IS-065 run-synthetic service (Model A bounded feed)"
```

---

### Task 4: Create-from-synthetic REST endpoint

**Files:**
- Create: `api/src/main/java/com/ainclusive/iotsim/api/synthetic/SyntheticSourceController.java`
- Test: `app/src/test/java/com/ainclusive/iotsim/app/SyntheticSourceControllerTest.java`

**Interfaces:**
- Consumes: `SyntheticSourceService.create`; `SyntheticConfig`; `DataSourceController.DataSourceResponse.from`.
- Produces: `POST /api/v1/projects/{projectId}/data-sources/synthetic`; `SyntheticSourceController.CreateSyntheticSourceRequest(String name, String protocol, String endpoint, SyntheticConfig config)`.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/ainclusive/iotsim/app/SyntheticSourceControllerTest.java`:

```java
package com.ainclusive.iotsim.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ainclusive.iotsim.api.datasource.DataSourceController.DataSourceResponse;
import com.ainclusive.iotsim.api.synthetic.SyntheticSourceController;
import com.ainclusive.iotsim.api.synthetic.SyntheticSourceController.CreateSyntheticSourceRequest;
import com.ainclusive.iotsim.domain.datasource.CredentialState;
import com.ainclusive.iotsim.domain.datasource.DataSource;
import com.ainclusive.iotsim.domain.datasource.Protocol;
import com.ainclusive.iotsim.domain.datasource.RuntimeState;
import com.ainclusive.iotsim.domain.datasource.SourceBasis;
import com.ainclusive.iotsim.domain.synthetic.PatternSpec;
import com.ainclusive.iotsim.domain.synthetic.SyntheticConfig;
import com.ainclusive.iotsim.domain.synthetic.SyntheticSourceService;
import com.ainclusive.iotsim.domain.synthetic.SyntheticVariableConfig;
import com.ainclusive.iotsim.protocolmodel.DataType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class SyntheticSourceControllerTest {

    private static final String PROJECT = "p1";

    private SyntheticSourceService service;
    private SyntheticSourceController controller;

    @BeforeEach
    void setUp() {
        service = mock(SyntheticSourceService.class);
        controller = new SyntheticSourceController(service);
    }

    private static SyntheticConfig config() {
        return new SyntheticConfig(1L, List.of(new SyntheticVariableConfig("n", DataType.FLOAT64,
                new PatternSpec("CONSTANT", 1.0, null, null, null, null, null, null), 100)));
    }

    private static DataSource sample() {
        Instant now = Instant.now();
        return new DataSource("ds1", PROJECT, "Gen", Protocol.OPC_UA, SourceBasis.SYNTHETIC,
                "sc1", 1, "{}", "{}", false, RuntimeState.STOPPED, CredentialState.MISSING, now, now, "local", 0);
    }

    @Test
    void createReturns201WithBody() {
        given(service.create(eq(PROJECT), eq("Gen"), eq("OPC_UA"), any(), any(), eq("local")))
                .willReturn(sample());
        ResponseEntity<DataSourceResponse> resp = controller.create(
                PROJECT, new CreateSyntheticSourceRequest("Gen", "OPC_UA", "{}", config()));
        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(resp.getHeaders().getETag()).isEqualTo("\"0\"");
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().basis()).isEqualTo("SYNTHETIC");
    }

    @Test
    void blankNameRejected() {
        assertThatThrownBy(() -> controller.create(
                PROJECT, new CreateSyntheticSourceRequest(" ", "OPC_UA", "{}", config())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingConfigRejected() {
        assertThatThrownBy(() -> controller.create(
                PROJECT, new CreateSyntheticSourceRequest("Gen", "OPC_UA", "{}", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("config is required");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:test --tests "com.ainclusive.iotsim.app.SyntheticSourceControllerTest"`
Expected: FAIL — `SyntheticSourceController` does not exist.

- [ ] **Step 3: Write `SyntheticSourceController`**

`api/src/main/java/com/ainclusive/iotsim/api/synthetic/SyntheticSourceController.java`:

```java
package com.ainclusive.iotsim.api.synthetic;

import com.ainclusive.iotsim.api.datasource.DataSourceController.DataSourceResponse;
import com.ainclusive.iotsim.domain.datasource.DataSource;
import com.ainclusive.iotsim.domain.synthetic.SyntheticConfig;
import com.ainclusive.iotsim.domain.synthetic.SyntheticSourceService;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Create-from-synthetic data sources (backend-specs/05; IS-065). */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/data-sources/synthetic")
public class SyntheticSourceController {

    private final SyntheticSourceService syntheticSources;

    public SyntheticSourceController(SyntheticSourceService syntheticSources) {
        this.syntheticSources = syntheticSources;
    }

    @PostMapping
    public ResponseEntity<DataSourceResponse> create(
            @PathVariable String projectId, @RequestBody CreateSyntheticSourceRequest req) {
        if (req == null || req.name() == null || req.name().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (req.protocol() == null || req.protocol().isBlank()) {
            throw new IllegalArgumentException("protocol is required");
        }
        if (req.config() == null) {
            throw new IllegalArgumentException("config is required");
        }
        DataSource ds = syntheticSources.create(
                projectId, req.name(), req.protocol(), req.endpoint(), req.config(), "local");
        return ResponseEntity.created(
                        URI.create("/api/v1/projects/" + projectId + "/data-sources/" + ds.id()))
                .eTag("\"" + ds.version() + "\"")
                .body(DataSourceResponse.from(ds));
    }

    public record CreateSyntheticSourceRequest(
            String name, String protocol, String endpoint, SyntheticConfig config) {}
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:test --tests "com.ainclusive.iotsim.app.SyntheticSourceControllerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/com/ainclusive/iotsim/api/synthetic/SyntheticSourceController.java \
        app/src/test/java/com/ainclusive/iotsim/app/SyntheticSourceControllerTest.java
git commit -m "feat(api): IS-065 create-from-synthetic endpoint"
```

---

### Task 5: Run-synthetic REST endpoint

**Files:**
- Create: `api/src/main/java/com/ainclusive/iotsim/api/synthetic/SyntheticRunController.java`
- Test: `app/src/test/java/com/ainclusive/iotsim/app/SyntheticRunControllerTest.java`

**Interfaces:**
- Consumes: `SyntheticRunService.run`; `SyntheticRunSummary`.
- Produces: `POST /api/v1/projects/{projectId}/data-sources/{dataSourceId}/run-synthetic`; `SyntheticRunController.SyntheticRunRequest(long durationMs)`; `SyntheticRunController.SyntheticRunResponse(String dataSourceId, long valueCount, long seed, String runId, String evidenceId)`.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/ainclusive/iotsim/app/SyntheticRunControllerTest.java`:

```java
package com.ainclusive.iotsim.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ainclusive.iotsim.api.synthetic.SyntheticRunController;
import com.ainclusive.iotsim.api.synthetic.SyntheticRunController.SyntheticRunRequest;
import com.ainclusive.iotsim.api.synthetic.SyntheticRunController.SyntheticRunResponse;
import com.ainclusive.iotsim.domain.synthetic.SyntheticRunService;
import com.ainclusive.iotsim.domain.synthetic.SyntheticRunSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SyntheticRunControllerTest {

    private static final String PROJECT = "p1";
    private static final String SOURCE = "ds1";

    private SyntheticRunService service;
    private SyntheticRunController controller;

    @BeforeEach
    void setUp() {
        service = mock(SyntheticRunService.class);
        controller = new SyntheticRunController(service);
    }

    @Test
    void runReturnsSummary() {
        given(service.run(PROJECT, SOURCE, 1000L))
                .willReturn(new SyntheticRunSummary(SOURCE, 14, 5L, "run-1", "ev-1"));
        SyntheticRunResponse resp = controller.run(PROJECT, SOURCE, new SyntheticRunRequest(1000L));
        assertThat(resp.valueCount()).isEqualTo(14);
        assertThat(resp.seed()).isEqualTo(5L);
        assertThat(resp.runId()).isEqualTo("run-1");
        assertThat(resp.evidenceId()).isEqualTo("ev-1");
    }

    @Test
    void invalidDurationPropagatesBadRequest() {
        given(service.run(PROJECT, SOURCE, 0L))
                .willThrow(new IllegalArgumentException("durationMs must be > 0: 0"));
        assertThatThrownBy(() -> controller.run(PROJECT, SOURCE, new SyntheticRunRequest(0L)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:test --tests "com.ainclusive.iotsim.app.SyntheticRunControllerTest"`
Expected: FAIL — `SyntheticRunController` does not exist.

- [ ] **Step 3: Write `SyntheticRunController`**

`api/src/main/java/com/ainclusive/iotsim/api/synthetic/SyntheticRunController.java`:

```java
package com.ainclusive.iotsim.api.synthetic;

import com.ainclusive.iotsim.domain.synthetic.SyntheticRunService;
import com.ainclusive.iotsim.domain.synthetic.SyntheticRunSummary;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runs a synthetic source's generated series as a bounded batch (Model A; IS-065).
 * The generated twin of the replay endpoint.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/data-sources/{dataSourceId}/run-synthetic")
public class SyntheticRunController {

    private final SyntheticRunService syntheticRuns;

    public SyntheticRunController(SyntheticRunService syntheticRuns) {
        this.syntheticRuns = syntheticRuns;
    }

    @PostMapping
    public SyntheticRunResponse run(
            @PathVariable String projectId, @PathVariable String dataSourceId,
            @RequestBody SyntheticRunRequest req) {
        long durationMs = req == null ? 0 : req.durationMs();
        SyntheticRunSummary summary = syntheticRuns.run(projectId, dataSourceId, durationMs);
        return new SyntheticRunResponse(summary.dataSourceId(), summary.valueCount(),
                summary.seed(), summary.runId(), summary.evidenceId());
    }

    public record SyntheticRunRequest(long durationMs) {}

    public record SyntheticRunResponse(
            String dataSourceId, long valueCount, long seed, String runId, String evidenceId) {}
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:test --tests "com.ainclusive.iotsim.app.SyntheticRunControllerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/com/ainclusive/iotsim/api/synthetic/SyntheticRunController.java \
        app/src/test/java/com/ainclusive/iotsim/app/SyntheticRunControllerTest.java
git commit -m "feat(api): IS-065 run-synthetic endpoint"
```

---

### Task 6: Full build + catalog (flag deferred IS-119)

**Files:**
- Modify: `backend-specs/TASKS.md` (Wave D section + snapshot line)

- [ ] **Step 1: Run the whole build green**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. If you have Colima running and want the Testcontainers ITs to execute rather than skip, first export:
```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```
Then re-run `./gradlew build`.

- [ ] **Step 2: Add the deferred Model-B task IS-119 to the catalog**

In `backend-specs/TASKS.md`, under `## Wave D — …`, add after the IS-074 line:

```markdown
- [ ] IS-119 [BE] ⬜ [runtime] Run synthetic source — continuous live feed (Model B / real-time pacing); low priority, pairs with IS-069 — 02
```

- [ ] **Step 3: Update the snapshot counts line**

In `backend-specs/TASKS.md`, the snapshot line near the top currently reads:
`Snapshot: **build green, 186 tests / 41 suites, 0 skipped.** 66 done · 1 partial · 49 todo (116 total).`
Replace the counts with the post-change totals (IS-065 will flip to done via `/open-pr`; IS-119 is a new todo):
`Snapshot: **build green.** 67 done · 1 partial · 50 todo (117 total).`

(Leave the IS-065 checkbox itself unchecked here — `/open-pr` flips it in the same PR to satisfy the catalog-sync CI gate.)

- [ ] **Step 4: Commit**

```bash
git add backend-specs/TASKS.md
git commit -m "docs: IS-065 add deferred Model-B task IS-119 + snapshot"
```

- [ ] **Step 5: Mirror IS-119 to the board**

Run the board reconcile so Project #1 gains the IS-119 issue: invoke `/board-sync IS-119` (creates the issue, sets Status=Todo, Area=BE). This is a board write — confirm it succeeded.

---

## After implementation

- Open the PR with the `/open-pr` skill from this branch: it runs the Definition-of-Done checks, flips the IS-065 catalog checkbox in the same PR (catalog-sync gate), creates the PR with `Implements: IS-065`, arms squash auto-merge, and moves the board to In review.
- Then work the automated review with `/review-loop`.

## Self-review notes (author check)

- **Spec coverage:** §2 config → Task 1; §3 create flow + REST → Tasks 2, 4; §4 run flow + REST → Tasks 3, 5; §5 errors → covered by IllegalArgumentException/ResourceNotFoundException tests in Tasks 2–5; §6 testing → each task's tests; §7 artifacts → Tasks 1–5; IS-119 follow-up + catalog/board → Task 6.
- **Type consistency:** `SyntheticConfig`, `SyntheticVariableConfig`, `PatternSpec`/`PatternSpec.StepSpec`, `SyntheticConfigMapper.toVariables/toPattern`, `SyntheticSourceService.create`, `SyntheticRunService.run`, `SyntheticRunSummary(dataSourceId, valueCount, seed, runId, evidenceId)`, and the two controllers' request/response records are referenced identically across tasks.
- **No DB migration** (verified: `basis` and `runs.kind` already allow `SYNTHETIC`).
