# IS-054 Project Overview Aggregation — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a read-only `GET /api/v1/projects/overview` endpoint returning, for every project, its configured-source / running-source / reusable-artifact counts plus an attention count of unhealthy sources.

**Architecture:** A new domain `ProjectOverviewService` composes the existing `ProjectService`, `DataSourceService` and `RecordingService` (so running/attention counts reuse the runtime-state derivation that is the single source of truth). A thin `ProjectOverviewController` in the api module exposes it as a JSON collection. No persistence changes, no new dependencies.

**Tech Stack:** Java 25, Spring Boot 4.1, Spring Web MVC, JUnit 5 + AssertJ (domain), Mockito (app/web tests), Gradle Kotlin-DSL multi-module.

**Design doc:** `docs/superpowers/specs/2026-06-30-is-054-project-overview-design.md`

## Global Constraints

- **Java toolchain 25**; modules use the `buildlogic.java-conventions` plugin.
- **API stays `/api/v1`** — never introduce `/api/v2` ([[api-version-only-bump-on-request]]).
- **Module boundaries are ArchUnit-enforced**: domain code must not import api/app types; the controller lives in `api`, business logic in `domain`.
- **Spotless import order**: static imports first, then regular imports alphabetical by package; files end with a newline, no trailing whitespace.
- **Beans have exactly one constructor** (constructor injection) — no `@Autowired` needed.
- **Read-only view**: no ETag / If-Match on this endpoint.
- **Attention = `runtimeState ∈ {ERROR, STALE}`**; **running = `runtimeState == RUNNING`**.
- Response field names verbatim: `projectId`, `name`, `configuredSources`, `runningSources`, `reusableArtifacts`, `sourcesNeedingAttention`.

---

### Task 1: Domain — `ProjectOverview` record + `ProjectOverviewService`

**Files:**
- Create: `domain/src/main/java/com/ainclusive/iotsim/domain/project/ProjectOverview.java`
- Create: `domain/src/main/java/com/ainclusive/iotsim/domain/project/ProjectOverviewService.java`
- Test: `domain/src/test/java/com/ainclusive/iotsim/domain/project/ProjectOverviewServiceTest.java`

**Interfaces:**
- Consumes (existing): `ProjectService.list() : List<Project>`, `Project.id() : String`, `Project.name() : String`; `DataSourceService.list(String projectId) : List<DataSource>`, `DataSource.runtimeState() : RuntimeState`; `RecordingService.list(String projectId) : List<Recording>`; enum `RuntimeState{STOPPED,STARTING,RUNNING,ERROR,STALE}`.
- Produces (for Task 2): record `ProjectOverview(String projectId, String name, int configuredSources, int runningSources, int reusableArtifacts, int sourcesNeedingAttention)`; `ProjectOverviewService.overview() : List<ProjectOverview>`.

- [ ] **Step 1: Create the `ProjectOverview` record** (needed by the test and service)

`domain/src/main/java/com/ainclusive/iotsim/domain/project/ProjectOverview.java`:

```java
package com.ainclusive.iotsim.domain.project;

/**
 * Per-project rollup for the workspace overview (IS-054). All counts are derived,
 * never persisted; {@code sourcesNeedingAttention} counts data sources whose
 * runtime state is unhealthy (ERROR or STALE). See
 * docs/superpowers/specs/2026-06-30-is-054-project-overview-design.md.
 */
public record ProjectOverview(
        String projectId,
        String name,
        int configuredSources,
        int runningSources,
        int reusableArtifacts,
        int sourcesNeedingAttention) {
}
```

- [ ] **Step 2: Write the failing test**

`domain/src/test/java/com/ainclusive/iotsim/domain/project/ProjectOverviewServiceTest.java`:

```java
package com.ainclusive.iotsim.domain.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.domain.datasource.CredentialState;
import com.ainclusive.iotsim.domain.datasource.DataSource;
import com.ainclusive.iotsim.domain.datasource.DataSourceService;
import com.ainclusive.iotsim.domain.datasource.Protocol;
import com.ainclusive.iotsim.domain.datasource.RuntimeState;
import com.ainclusive.iotsim.domain.datasource.SourceBasis;
import com.ainclusive.iotsim.domain.recording.Recording;
import com.ainclusive.iotsim.domain.recording.RecordingService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProjectOverviewServiceTest {

    @Test
    void aggregatesCountsRunningAttentionAndArtifactsForAProject() {
        Map<String, List<DataSource>> sources = Map.of("p1", List.of(
                source("a", RuntimeState.RUNNING),
                source("b", RuntimeState.RUNNING),
                source("c", RuntimeState.ERROR),
                source("d", RuntimeState.STALE),
                source("e", RuntimeState.STOPPED)));
        Map<String, List<Recording>> recs = Map.of("p1", List.of(
                recording("r1"), recording("r2"), recording("r3")));

        ProjectOverviewService service = service(List.of(project("p1", "Line 1")), sources, recs);

        List<ProjectOverview> overview = service.overview();

        assertThat(overview).hasSize(1);
        ProjectOverview o = overview.get(0);
        assertThat(o.projectId()).isEqualTo("p1");
        assertThat(o.name()).isEqualTo("Line 1");
        assertThat(o.configuredSources()).isEqualTo(5);
        assertThat(o.runningSources()).isEqualTo(2);
        assertThat(o.sourcesNeedingAttention()).isEqualTo(2); // ERROR + STALE only
        assertThat(o.reusableArtifacts()).isEqualTo(3);
    }

    @Test
    void returnsOneRowPerProjectInListOrderWithIndependentTallies() {
        Map<String, List<DataSource>> sources = Map.of(
                "p1", List.of(source("a", RuntimeState.RUNNING)),
                "p2", List.of(source("b", RuntimeState.STOPPED), source("c", RuntimeState.RUNNING)));
        Map<String, List<Recording>> recs = Map.of(
                "p1", List.of(),
                "p2", List.of(recording("r1")));

        ProjectOverviewService service = service(
                List.of(project("p1", "First"), project("p2", "Second")), sources, recs);

        List<ProjectOverview> overview = service.overview();

        assertThat(overview).extracting(ProjectOverview::projectId).containsExactly("p1", "p2");
        assertThat(overview.get(0).runningSources()).isEqualTo(1);
        assertThat(overview.get(1).configuredSources()).isEqualTo(2);
        assertThat(overview.get(1).runningSources()).isEqualTo(1);
        assertThat(overview.get(1).reusableArtifacts()).isEqualTo(1);
    }

    @Test
    void emptyProjectListYieldsEmptyOverview() {
        assertThat(service(List.of(), Map.of(), Map.of()).overview()).isEmpty();
    }

    // --- test doubles: override only the read methods the aggregator calls ---

    private static ProjectOverviewService service(List<Project> projects,
            Map<String, List<DataSource>> sourcesByProject,
            Map<String, List<Recording>> recordingsByProject) {
        ProjectService projectService = new ProjectService(null) {
            @Override
            public List<Project> list() {
                return projects;
            }
        };
        DataSourceService dataSourceService = new DataSourceService(null, null, null, null, null) {
            @Override
            public List<DataSource> list(String projectId) {
                return sourcesByProject.getOrDefault(projectId, List.of());
            }
        };
        RecordingService recordingService = new RecordingService(null, null, null, null, null, null) {
            @Override
            public List<Recording> list(String projectId) {
                return recordingsByProject.getOrDefault(projectId, List.of());
            }
        };
        return new ProjectOverviewService(projectService, dataSourceService, recordingService);
    }

    private static Project project(String id, String name) {
        Instant now = Instant.now();
        return new Project(id, name, null, Project.ProjectStatus.ACTIVE, now, now, "local", 0);
    }

    private static DataSource source(String id, RuntimeState state) {
        Instant now = Instant.now();
        return new DataSource(id, "p1", "src-" + id, Protocol.OPC_UA, SourceBasis.MANUAL,
                null, null, "{}", "{}", false, state, CredentialState.MISSING, now, now, "local", 0);
    }

    private static Recording recording(String id) {
        return new Recording(id, "p1", "ds", 1, "SCAN_RECORD", 0L, Instant.now(), "local", 0);
    }
}
```

- [ ] **Step 3: Run the test — verify it FAILS (red)**

Run: `./gradlew :domain:test --tests "com.ainclusive.iotsim.domain.project.ProjectOverviewServiceTest"`
Expected: **compilation failure** — `ProjectOverviewService` does not exist yet.

- [ ] **Step 4: Implement `ProjectOverviewService`**

`domain/src/main/java/com/ainclusive/iotsim/domain/project/ProjectOverviewService.java`:

```java
package com.ainclusive.iotsim.domain.project;

import com.ainclusive.iotsim.domain.datasource.DataSource;
import com.ainclusive.iotsim.domain.datasource.DataSourceService;
import com.ainclusive.iotsim.domain.datasource.RuntimeState;
import com.ainclusive.iotsim.domain.recording.RecordingService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Aggregates per-project rollups for the workspace overview (IS-054): configured
 * and running data-source counts, reusable-artifact (recording) counts, and an
 * attention count of sources in an unhealthy runtime state (ERROR or STALE).
 * Composes the existing project, data-source and recording services so runtime
 * state uses the same source of truth as the data-source API.
 * See backend-specs/05_API_CONTRACT.md and the IS-054 design doc.
 */
@Service
public class ProjectOverviewService {

    private final ProjectService projects;
    private final DataSourceService dataSources;
    private final RecordingService recordings;

    public ProjectOverviewService(ProjectService projects, DataSourceService dataSources,
            RecordingService recordings) {
        this.projects = projects;
        this.dataSources = dataSources;
        this.recordings = recordings;
    }

    /** One rollup per project, in {@link ProjectService#list()} order. */
    public List<ProjectOverview> overview() {
        return projects.list().stream().map(this::aggregate).toList();
    }

    private ProjectOverview aggregate(Project project) {
        List<DataSource> sources = dataSources.list(project.id());
        int running = (int) sources.stream()
                .filter(s -> s.runtimeState() == RuntimeState.RUNNING)
                .count();
        int attention = (int) sources.stream()
                .filter(s -> s.runtimeState() == RuntimeState.ERROR
                        || s.runtimeState() == RuntimeState.STALE)
                .count();
        int artifacts = recordings.list(project.id()).size();
        return new ProjectOverview(
                project.id(), project.name(), sources.size(), running, artifacts, attention);
    }
}
```

- [ ] **Step 5: Run the test — verify it PASSES (green)**

Run: `./gradlew :domain:test --tests "com.ainclusive.iotsim.domain.project.ProjectOverviewServiceTest"`
Expected: **PASS**, 3 tests.

- [ ] **Step 6: Commit**

```bash
git add domain/src/main/java/com/ainclusive/iotsim/domain/project/ProjectOverview.java \
        domain/src/main/java/com/ainclusive/iotsim/domain/project/ProjectOverviewService.java \
        domain/src/test/java/com/ainclusive/iotsim/domain/project/ProjectOverviewServiceTest.java
git commit -m "feat(observ): IS-054 project overview aggregation service

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: API — `ProjectOverviewController` + response DTO

**Files:**
- Create: `api/src/main/java/com/ainclusive/iotsim/api/project/ProjectOverviewController.java`
- Test: `app/src/test/java/com/ainclusive/iotsim/app/ProjectOverviewControllerTest.java`

**Interfaces:**
- Consumes (from Task 1): `ProjectOverviewService.overview() : List<ProjectOverview>` and the `ProjectOverview` record accessors.
- Produces: `GET /api/v1/projects/overview` → `200`, `List<ProjectOverviewResponse>`; nested record `ProjectOverviewResponse(String projectId, String name, int configuredSources, int runningSources, int reusableArtifacts, int sourcesNeedingAttention)` with static `from(ProjectOverview)`.

- [ ] **Step 1: Write the failing controller test**

`app/src/test/java/com/ainclusive/iotsim/app/ProjectOverviewControllerTest.java`:

```java
package com.ainclusive.iotsim.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ainclusive.iotsim.api.project.ProjectOverviewController;
import com.ainclusive.iotsim.api.project.ProjectOverviewController.ProjectOverviewResponse;
import com.ainclusive.iotsim.domain.project.ProjectOverview;
import com.ainclusive.iotsim.domain.project.ProjectOverviewService;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit test for {@link ProjectOverviewController}. */
class ProjectOverviewControllerTest {

    private final ProjectOverviewService service = mock(ProjectOverviewService.class);
    private final ProjectOverviewController controller = new ProjectOverviewController(service);

    @Test
    void overviewMapsDomainRollupsToResponseFields() {
        given(service.overview()).willReturn(List.of(
                new ProjectOverview("p1", "Line 1", 5, 2, 3, 1),
                new ProjectOverview("p2", "Line 2", 0, 0, 0, 0)));

        List<ProjectOverviewResponse> resp = controller.overview();

        assertThat(resp).hasSize(2);
        ProjectOverviewResponse first = resp.get(0);
        assertThat(first.projectId()).isEqualTo("p1");
        assertThat(first.name()).isEqualTo("Line 1");
        assertThat(first.configuredSources()).isEqualTo(5);
        assertThat(first.runningSources()).isEqualTo(2);
        assertThat(first.reusableArtifacts()).isEqualTo(3);
        assertThat(first.sourcesNeedingAttention()).isEqualTo(1);
    }

    @Test
    void overviewReturnsEmptyListWhenNoProjects() {
        given(service.overview()).willReturn(List.of());
        assertThat(controller.overview()).isEmpty();
    }
}
```

- [ ] **Step 2: Run the test — verify it FAILS (red)**

Run: `./gradlew :app:test --tests "com.ainclusive.iotsim.app.ProjectOverviewControllerTest"`
Expected: **compilation failure** — `ProjectOverviewController` does not exist yet.

- [ ] **Step 3: Implement the controller + DTO**

`api/src/main/java/com/ainclusive/iotsim/api/project/ProjectOverviewController.java`:

```java
package com.ainclusive.iotsim.api.project;

import com.ainclusive.iotsim.domain.project.ProjectOverview;
import com.ainclusive.iotsim.domain.project.ProjectOverviewService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Project overview aggregation (IS-054): per-project configured/running source
 * counts, reusable-artifact counts, and an attention count of unhealthy sources.
 * Read-only derived view — no ETag/concurrency. The literal {@code /overview}
 * segment is matched ahead of {@code ProjectController}'s {@code /{id}} pattern.
 * See backend-specs/05_API_CONTRACT.md.
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectOverviewController {

    private final ProjectOverviewService overview;

    public ProjectOverviewController(ProjectOverviewService overview) {
        this.overview = overview;
    }

    @GetMapping("/overview")
    public List<ProjectOverviewResponse> overview() {
        return overview.overview().stream().map(ProjectOverviewResponse::from).toList();
    }

    public record ProjectOverviewResponse(
            String projectId,
            String name,
            int configuredSources,
            int runningSources,
            int reusableArtifacts,
            int sourcesNeedingAttention) {

        static ProjectOverviewResponse from(ProjectOverview o) {
            return new ProjectOverviewResponse(
                    o.projectId(), o.name(), o.configuredSources(), o.runningSources(),
                    o.reusableArtifacts(), o.sourcesNeedingAttention());
        }
    }
}
```

- [ ] **Step 4: Run the test — verify it PASSES (green)**

Run: `./gradlew :app:test --tests "com.ainclusive.iotsim.app.ProjectOverviewControllerTest"`
Expected: **PASS**, 2 tests.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/com/ainclusive/iotsim/api/project/ProjectOverviewController.java \
        app/src/test/java/com/ainclusive/iotsim/app/ProjectOverviewControllerTest.java
git commit -m "feat(api): IS-054 GET /projects/overview endpoint

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Full multi-module build verification

**Files:** none (verification only).

**Interfaces:** none.

- [ ] **Step 1: Run the full build** ([[always-compile-and-test]])

The new beans (`ProjectOverviewService`, `ProjectOverviewController`) are picked up by component scan automatically; `ApplicationSmokeIT` boots the full context and will catch any wiring problem (e.g. ambiguous `/overview` vs `/{id}` mapping or a missing dependency).

To exercise the Testcontainers ITs locally under Colima, export first ([[testcontainers-colima-env]]); otherwise they skip silently (they run in CI regardless):

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
./gradlew build
```

Expected: `BUILD SUCCESSFUL`; Spotless, Checkstyle and ArchUnit (module-boundary) checks all pass; the two new test classes run green.

- [ ] **Step 2: If Spotless reformats, restage and amend**

If the build fails on `spotlessJavaCheck`, run `./gradlew spotlessApply`, then:

```bash
git add -A
git commit --amend --no-edit
```

Re-run `./gradlew build` and confirm `BUILD SUCCESSFUL`.

---

## Self-Review

**Spec coverage:**
- `GET /api/v1/projects/overview` collection → Task 2. ✓
- Response fields `projectId/name/configuredSources/runningSources/reusableArtifacts/sourcesNeedingAttention` → `ProjectOverview` (Task 1) + DTO (Task 2). ✓
- `ProjectOverviewService` composes the three services; runtime state via data-source service → Task 1. ✓
- running = RUNNING; attention = ERROR + STALE → Task 1 test assertions. ✓
- read-only, no ETag → controller has no `If-Match`/ETag (Task 2). ✓
- one row per project in list order; empty → `[]` → Task 1 + Task 2 tests. ✓
- domain unit test + controller web test → Tasks 1 & 2. ✓
- build green incl. boundaries → Task 3. ✓
- `lastActivity` deferred to IS-055 → intentionally absent. ✓

**Placeholder scan:** none — every code/test block is complete; commands have expected output.

**Type consistency:** `ProjectOverview` field names/types and `overview()` signature are identical across Task 1 (definition), Task 1 test, Task 2 controller, and Task 2 test. DataSource/Recording/Project constructor arities match the records read from source.

## Execution handoff note

This plan stops at "code complete + full build green." Opening the PR is a separate project workflow: run **`/open-pr`** afterward — it flips the `IS-054` checkbox in `backend-specs/TASKS.md` in the same PR (the `catalog-sync` CI gate), creates the PR with `Implements: IS-054`, arms squash auto-merge, and moves the board to **In review**. Then run **`/review-loop`**.
