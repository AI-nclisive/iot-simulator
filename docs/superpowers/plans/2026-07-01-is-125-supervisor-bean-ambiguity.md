# IS-125 Supervisor bean ambiguity — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans (inline; tiny fix) or subagent-driven-development. Steps use checkbox syntax.

**Goal:** Make the Spring context start in `IOTSIM_RUNTIME_MODE=supervisor` by resolving the ambiguous `SourceCapturer`/`SourceScanner` beans, and add a supervisor-mode context-boot test.

**Architecture:** `@Primary` on the dedicated `sourceCapturer`/`sourceScanner` beans in `RuntimeConfig` so they win injection over the `runtimeController` bean's incidental interface match; a `@SpringBootTest` boots supervisor mode against Testcontainers Postgres.

**Tech Stack:** Java 21, Spring (`@Primary`), JUnit 5 + AssertJ, Testcontainers (Postgres 17), `@SpringBootTest`.

## Global Constraints

- Fix is DI-only in `RuntimeConfig`; no consumer changes; memory mode behavior unchanged.
- The supervisor-mode test boots the context only (no worker spawn — workers spawn on source start), so it needs no worker binary; it needs Docker/Postgres (Colima env for local run).
- After: `./gradlew build` green; IS-125 catalog line added `[x]` via `/open-pr`.

---

## Task 1: `@Primary` beans + supervisor context-boot test

**Files:**
- Modify: `app/src/main/java/com/ainclusive/iotsim/app/config/RuntimeConfig.java`
- Test: `app/src/test/java/com/ainclusive/iotsim/app/SupervisorModeContextIT.java`

- [ ] **Step 1: Write the failing supervisor-mode boot IT**

`SupervisorModeContextIT.java` (mirror `ApplicationSmokeIT`'s Testcontainers + datasource wiring; add the supervisor property):
```java
package com.ainclusive.iotsim.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Boots the app in supervisor runtime mode to prove the context wires without
 *  ambiguous SourceCapturer/SourceScanner beans (IS-125). No worker is spawned. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class SupervisorModeContextIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("iotsim.runtime.mode", () -> "supervisor");
    }

    @Value("${local.server.port}")
    int port;

    @Test
    void contextBootsInSupervisorMode() throws Exception {
        HttpResponse<String> resp = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(resp.body()).contains("\"status\":\"UP\"");
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
DOCKER_HOST=unix://$HOME/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
./gradlew :app:test --tests '*SupervisorModeContextIT' --rerun-tasks
```
Expected: FAIL — context fails to start with `expected single matching bean but found 2: runtimeController, sourceCapturer`.

- [ ] **Step 3: Add `@Primary` to the `runtimeController` bean**

In `RuntimeConfig.java`, import `org.springframework.context.annotation.Primary` and annotate the `runtimeController` bean method (NOT the helper beans — in supervisor mode the Supervisor is registered under all three bean names, so marking the helpers `@Primary` yields two primaries for `SourceScanner`/`SourceCapturer` and still fails; one primary on the controller resolves every interface to the single Supervisor):
```java
    @Bean
    @Primary
    public RuntimeController runtimeController(RuntimeProperties props,
            DataSourceRepository dataSources, RuntimeEventRepository runtimeEvents,
            ClientConnectionRepository clientConnections, ObjectMapper json,
            ExecutorService runtimeEventExecutor, LiveEventHub liveEventHub,
            LiveValuesHub liveValuesHub) {
        // ... body unchanged (supervisor vs InMemoryRuntimeController)
    }
```
Leave `sourceScanner`/`sourceCapturer` beans unannotated.

- [ ] **Step 4: Run to verify it passes**

```bash
DOCKER_HOST=unix://$HOME/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
./gradlew :app:test --tests '*SupervisorModeContextIT' --rerun-tasks
```
Expected: PASS (context boots in supervisor mode).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ainclusive/iotsim/app/config/RuntimeConfig.java \
        app/src/test/java/com/ainclusive/iotsim/app/SupervisorModeContextIT.java
git commit -m "fix(IS-125): @Primary source scanner/capturer beans so supervisor mode boots"
```

---

## Task 2: Full verification

- [ ] **Step 1: Full build**

```bash
DOCKER_HOST=unix://$HOME/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
./gradlew build --rerun-tasks
```
Expected: BUILD SUCCESSFUL — `SupervisorModeContextIT` + existing `ApplicationSmokeIT` (memory mode) both pass, confirming both modes boot.

- [ ] **Step 2: Open the PR** — `/open-pr` (add + `[x]` the `IS-125` catalog line; `Implements: IS-125`; arm auto-merge; board → In review).

---

## Self-Review

- **Spec coverage:** `@Primary` fix → Task 1 Step 3; supervisor context-boot test → Task 1 Step 1; both modes boot → Task 2. ✅
- **Placeholders:** none.
- **Consistency:** `iotsim.runtime.mode=supervisor` property + `@Primary` on both `sourceScanner`/`sourceCapturer` used consistently; test mirrors `ApplicationSmokeIT` wiring verbatim.
