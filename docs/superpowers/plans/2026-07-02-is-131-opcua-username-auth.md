# IS-131 — OPC UA Username/Password Authentication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a simulated OPC UA data source run with username/password authentication (or stay anonymous), configured per source and reproducible across export/import.

**Architecture:** A new `security_config` jsonb column carries a per-source auth model (passwords hashed). It flows `DB row → EndpointSecurity (platform model) → RuntimeStartSpec → Supervisor → proto SecurityConfig on ConfigureRequest → worker`. The Milo server advertises the configured user-token policies and validates username/password via a `UsernameIdentityValidator`. Password hashing is pure-JDK PBKDF2 in `protocol-model`, shared by domain (hash on write) and worker (verify on login).

**Tech Stack:** Java 21, Spring Boot 4, jOOQ + Flyway (DDLDatabase codegen — no live DB), Eclipse Milo 0.6.16, gRPC/protobuf, Jackson 3 (`tools.jackson.*`), JUnit 5 + AssertJ, Testcontainers.

## Global Constraints

- Worker-contract version bumps **`1.2.0 → 1.3.0`** (additive proto field; major unchanged so the `Hello` handshake still matches).
- **Empty `security_config` / empty `SecurityConfig` ≡ current behaviour** (SecurityPolicy `None`, `MessageSecurityMode` `None`, Anonymous only). All existing worker/supervisor tests must stay green.
- **No new dependency** (`STACK.md` governance): password hashing is pure-JDK PBKDF2 (`javax.crypto`).
- Passwords are **never stored or exported in plaintext** — only a salted hash (`passwordHash`). Plaintext enters only via API write; the API response and any log must never carry `passwordHash` or plaintext.
- REST API stays `/api/v1` (no version bump).
- Jackson is **Jackson 3**: import `tools.jackson.*`, `JsonNode.asString(...)`/`asBoolean(...)`, catch unchecked exceptions.
- Before opening the PR run the **full module `check`** (not `--tests X`) so Spotless/Checkstyle catch unused-import violations. Worker/persistence ITs skip silently under `./gradlew build` unless `DOCKER_HOST` + `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` are exported (Colima).
- Milo swallows bind failures — the runtime already detects them via `getBoundEndpoints().isEmpty()`; do not remove that check.

---

### Task 1: `PasswordHash` utility (protocol-model)

**Files:**
- Create: `protocol-model/src/main/java/com/ainclusive/iotsim/protocolmodel/PasswordHash.java`
- Test: `protocol-model/src/test/java/com/ainclusive/iotsim/protocolmodel/PasswordHashTest.java`

**Interfaces:**
- Produces: `PasswordHash.encode(String plaintext) -> String` (format `pbkdf2-sha256$<iterations>$<b64 salt>$<b64 hash>`); `PasswordHash.matches(String plaintext, String encoded) -> boolean` (constant-time; `false` on malformed/null).

- [ ] **Step 1: Write the failing test**

```java
package com.ainclusive.iotsim.protocolmodel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordHashTest {

    @Test
    void encodesAndVerifiesCorrectPassword() {
        String encoded = PasswordHash.encode("s3cret");
        assertThat(encoded).startsWith("pbkdf2-sha256$");
        assertThat(encoded).doesNotContain("s3cret");
        assertThat(PasswordHash.matches("s3cret", encoded)).isTrue();
        assertThat(PasswordHash.matches("wrong", encoded)).isFalse();
    }

    @Test
    void differentEncodingsForSamePasswordDueToSalt() {
        assertThat(PasswordHash.encode("pw")).isNotEqualTo(PasswordHash.encode("pw"));
    }

    @Test
    void rejectsMalformedOrNull() {
        assertThat(PasswordHash.matches("pw", null)).isFalse();
        assertThat(PasswordHash.matches(null, "pbkdf2-sha256$1$aa$bb")).isFalse();
        assertThat(PasswordHash.matches("pw", "not-a-hash")).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :protocol-model:test --tests '*PasswordHashTest'`
Expected: FAIL — `PasswordHash` does not exist (compile error).

- [ ] **Step 3: Write minimal implementation**

```java
package com.ainclusive.iotsim.protocolmodel;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Salted PBKDF2 password hashing (pure JDK — no dependency). Used by the domain to
 * hash a simulated OPC UA server's accepted passwords on write, and by the worker
 * to verify a client-supplied password on session activation. See
 * docs/superpowers/specs/2026-07-02-is-130-opcua-endpoint-security-design.md.
 */
public final class PasswordHash {

    private static final String PREFIX = "pbkdf2-sha256";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RNG = new SecureRandom();

    private PasswordHash() {}

    public static String encode(String plaintext) {
        byte[] salt = new byte[SALT_BYTES];
        RNG.nextBytes(salt);
        byte[] hash = pbkdf2(plaintext, salt, ITERATIONS);
        Base64.Encoder b64 = Base64.getEncoder();
        return PREFIX + "$" + ITERATIONS + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(hash);
    }

    public static boolean matches(String plaintext, String encoded) {
        if (plaintext == null || encoded == null) {
            return false;
        }
        String[] parts = encoded.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            return MessageDigest.isEqual(expected, pbkdf2(plaintext, salt, iterations));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static byte[] pbkdf2(String plaintext, byte[] salt, int iterations) {
        try {
            KeySpec spec = new PBEKeySpec(plaintext.toCharArray(), salt, iterations, KEY_BITS);
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2 unavailable", e);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :protocol-model:test --tests '*PasswordHashTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add protocol-model/src/main/java/com/ainclusive/iotsim/protocolmodel/PasswordHash.java \
        protocol-model/src/test/java/com/ainclusive/iotsim/protocolmodel/PasswordHashTest.java
git commit -m "feat(IS-131): PBKDF2 PasswordHash utility (pure JDK)"
```

---

### Task 2: `EndpointSecurity` neutral model (platform)

**Files:**
- Create: `platform/src/main/java/com/ainclusive/iotsim/platform/runtime/EndpointSecurity.java`
- Test: `platform/src/test/java/com/ainclusive/iotsim/platform/runtime/EndpointSecurityTest.java`

**Interfaces:**
- Produces: `record EndpointSecurity(boolean anonymousAllowed, boolean usernameEnabled, List<UserCredential> users)` with nested `record UserCredential(String username, String passwordHash)` and `static EndpointSecurity none()` (= `new EndpointSecurity(true, false, List.of())`). Canonical constructor defensively copies `users` (`null` → empty).

- [ ] **Step 1: Write the failing test**

```java
package com.ainclusive.iotsim.platform.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class EndpointSecurityTest {

    @Test
    void noneAllowsAnonymousOnly() {
        EndpointSecurity none = EndpointSecurity.none();
        assertThat(none.anonymousAllowed()).isTrue();
        assertThat(none.usernameEnabled()).isFalse();
        assertThat(none.users()).isEmpty();
    }

    @Test
    void copiesUsersDefensivelyAndTreatsNullAsEmpty() {
        assertThat(new EndpointSecurity(false, true, null).users()).isEmpty();
        List<EndpointSecurity.UserCredential> src =
                List.of(new EndpointSecurity.UserCredential("op", "hash"));
        assertThat(new EndpointSecurity(false, true, src).users()).containsExactlyElementsOf(src);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :platform:test --tests '*EndpointSecurityTest'`
Expected: FAIL — `EndpointSecurity` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.ainclusive.iotsim.platform.runtime;

import java.util.List;

/**
 * Neutral, per-source OPC UA endpoint security the worker enforces. Phase 1
 * (IS-131) carries user-token policy only; message security is added in IS-132.
 * {@link #none()} reproduces the historical None/Anonymous server.
 */
public record EndpointSecurity(boolean anonymousAllowed, boolean usernameEnabled, List<UserCredential> users) {

    public EndpointSecurity {
        users = users == null ? List.of() : List.copyOf(users);
    }

    public static EndpointSecurity none() {
        return new EndpointSecurity(true, false, List.of());
    }

    /** A username the server accepts and the salted hash of its password. */
    public record UserCredential(String username, String passwordHash) {}
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :platform:test --tests '*EndpointSecurityTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add platform/src/main/java/com/ainclusive/iotsim/platform/runtime/EndpointSecurity.java \
        platform/src/test/java/com/ainclusive/iotsim/platform/runtime/EndpointSecurityTest.java
git commit -m "feat(IS-131): EndpointSecurity neutral runtime model"
```

---

### Task 3: `EndpointSecurityCodec` (domain) — validate, hash, redact

**Files:**
- Create: `domain/src/main/java/com/ainclusive/iotsim/domain/datasource/EndpointSecurityCodec.java`
- Test: `domain/src/test/java/com/ainclusive/iotsim/domain/datasource/EndpointSecurityCodecTest.java`

**Interfaces:**
- Consumes: `PasswordHash.encode` (Task 1); `EndpointSecurity` (Task 2).
- Produces (package-private statics):
  - `normalizeForStorage(String rawJson) -> String` — API-write JSON (plaintext `password`) → storage JSON (`passwordHash`); validates; blank/`{}`/null → `"{}"`; throws `IllegalArgumentException` on invalid.
  - `toModel(String storedJson) -> EndpointSecurity` — storage JSON → model; blank → `EndpointSecurity.none()`.
  - `redact(String storedJson) -> String` — storage JSON → API-safe JSON (usernames + flags, no `passwordHash`).

**JSON shapes.** Write shape: `{"userTokens":{"anonymous":true,"username":{"enabled":true,"users":[{"username":"op","password":"pw"}]}}}`. Storage/redacted shapes replace `password` with `passwordHash` (redacted omits it entirely).

- [ ] **Step 1: Write the failing test**

```java
package com.ainclusive.iotsim.domain.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.platform.runtime.EndpointSecurity;
import com.ainclusive.iotsim.protocolmodel.PasswordHash;
import org.junit.jupiter.api.Test;

class EndpointSecurityCodecTest {

    private static final String WRITE_JSON =
            "{\"userTokens\":{\"anonymous\":false,\"username\":{\"enabled\":true,"
            + "\"users\":[{\"username\":\"operator\",\"password\":\"s3cret\"}]}}}";

    @Test
    void normalizeHashesPasswordAndDropsPlaintext() {
        String stored = EndpointSecurityCodec.normalizeForStorage(WRITE_JSON);
        assertThat(stored).contains("passwordHash").doesNotContain("s3cret").doesNotContain("\"password\"");
        EndpointSecurity model = EndpointSecurityCodec.toModel(stored);
        assertThat(model.anonymousAllowed()).isFalse();
        assertThat(model.usernameEnabled()).isTrue();
        assertThat(model.users()).singleElement().satisfies(u -> {
            assertThat(u.username()).isEqualTo("operator");
            assertThat(PasswordHash.matches("s3cret", u.passwordHash())).isTrue();
        });
    }

    @Test
    void blankBecomesNoneAndEmptyStorage() {
        assertThat(EndpointSecurityCodec.normalizeForStorage(null)).isEqualTo("{}");
        assertThat(EndpointSecurityCodec.normalizeForStorage("  ")).isEqualTo("{}");
        assertThat(EndpointSecurityCodec.toModel("{}")).isEqualTo(EndpointSecurity.none());
    }

    @Test
    void redactRemovesHashesKeepsUsernames() {
        String stored = EndpointSecurityCodec.normalizeForStorage(WRITE_JSON);
        String redacted = EndpointSecurityCodec.redact(stored);
        assertThat(redacted).contains("operator").doesNotContain("passwordHash");
    }

    @Test
    void rejectsUsernameEnabledWithNoUsers() {
        assertThatThrownBy(() -> EndpointSecurityCodec.normalizeForStorage(
                "{\"userTokens\":{\"anonymous\":false,\"username\":{\"enabled\":true,\"users\":[]}}}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNoAcceptedTokenType() {
        assertThatThrownBy(() -> EndpointSecurityCodec.normalizeForStorage(
                "{\"userTokens\":{\"anonymous\":false,\"username\":{\"enabled\":false}}}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankUsernameOrPassword() {
        assertThatThrownBy(() -> EndpointSecurityCodec.normalizeForStorage(
                "{\"userTokens\":{\"anonymous\":true,\"username\":{\"enabled\":true,"
                + "\"users\":[{\"username\":\"\",\"password\":\"x\"}]}}}"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :domain:test --tests '*EndpointSecurityCodecTest'`
Expected: FAIL — `EndpointSecurityCodec` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.ainclusive.iotsim.domain.datasource;

import com.ainclusive.iotsim.platform.runtime.EndpointSecurity;
import com.ainclusive.iotsim.protocolmodel.PasswordHash;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Translates the data-source endpoint-security JSON between its three shapes:
 * API-write (plaintext {@code password}), storage ({@code passwordHash}), and the
 * neutral {@link EndpointSecurity} runtime model. Hashes passwords on write and
 * redacts hashes for the API response. Passwords never leave this class in plaintext
 * once stored. See docs/superpowers/specs/2026-07-02-is-130-opcua-endpoint-security-design.md.
 */
final class EndpointSecurityCodec {

    private static final ObjectMapper JSON = new ObjectMapper();

    private EndpointSecurityCodec() {}

    static String normalizeForStorage(String rawJson) {
        if (isBlank(rawJson)) {
            return "{}";
        }
        JsonNode userTokens = parse(rawJson).path("userTokens");
        boolean anonymous = userTokens.path("anonymous").asBoolean(true);
        JsonNode username = userTokens.path("username");
        boolean usernameEnabled = username.path("enabled").asBoolean(false);
        if (!anonymous && !usernameEnabled) {
            throw new IllegalArgumentException("securityConfig: at least one user-token type must be accepted");
        }
        ArrayNode outUsers = JSON.createArrayNode();
        if (usernameEnabled) {
            JsonNode users = username.path("users");
            if (!users.isArray() || users.isEmpty()) {
                throw new IllegalArgumentException("securityConfig: username auth requires at least one user");
            }
            for (JsonNode u : users) {
                String name = u.path("username").asString("");
                String password = u.path("password").asString("");
                if (name.isBlank()) {
                    throw new IllegalArgumentException("securityConfig: user username must not be blank");
                }
                if (password.isEmpty()) {
                    throw new IllegalArgumentException("securityConfig: user password must not be blank");
                }
                ObjectNode outUser = JSON.createObjectNode();
                outUser.put("username", name);
                outUser.put("passwordHash", PasswordHash.encode(password));
                outUsers.add(outUser);
            }
        }
        return buildStored(anonymous, usernameEnabled, outUsers);
    }

    static EndpointSecurity toModel(String storedJson) {
        if (isBlank(storedJson)) {
            return EndpointSecurity.none();
        }
        JsonNode userTokens = parse(storedJson).path("userTokens");
        if (userTokens.isMissingNode()) {
            return EndpointSecurity.none();
        }
        boolean anonymous = userTokens.path("anonymous").asBoolean(true);
        JsonNode username = userTokens.path("username");
        boolean usernameEnabled = username.path("enabled").asBoolean(false);
        List<EndpointSecurity.UserCredential> users = new ArrayList<>();
        if (usernameEnabled) {
            for (JsonNode u : username.path("users")) {
                users.add(new EndpointSecurity.UserCredential(
                        u.path("username").asString(""), u.path("passwordHash").asString("")));
            }
        }
        return new EndpointSecurity(anonymous, usernameEnabled, users);
    }

    static String redact(String storedJson) {
        if (isBlank(storedJson)) {
            return "{}";
        }
        JsonNode userTokens = parse(storedJson).path("userTokens");
        if (userTokens.isMissingNode()) {
            return "{}";
        }
        boolean anonymous = userTokens.path("anonymous").asBoolean(true);
        JsonNode username = userTokens.path("username");
        boolean usernameEnabled = username.path("enabled").asBoolean(false);
        ArrayNode outUsers = JSON.createArrayNode();
        if (usernameEnabled) {
            for (JsonNode u : username.path("users")) {
                ObjectNode outUser = JSON.createObjectNode();
                outUser.put("username", u.path("username").asString(""));
                outUsers.add(outUser);
            }
        }
        return buildStored(anonymous, usernameEnabled, outUsers);
    }

    private static String buildStored(boolean anonymous, boolean usernameEnabled, ArrayNode users) {
        ObjectNode usernameNode = JSON.createObjectNode();
        usernameNode.put("enabled", usernameEnabled);
        usernameNode.set("users", users);
        ObjectNode userTokens = JSON.createObjectNode();
        userTokens.put("anonymous", anonymous);
        userTokens.set("username", usernameNode);
        ObjectNode root = JSON.createObjectNode();
        root.set("userTokens", userTokens);
        return root.toString();
    }

    private static JsonNode parse(String json) {
        try {
            return JSON.readTree(json);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("securityConfig must be valid JSON");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank() || "{}".equals(s.trim());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :domain:test --tests '*EndpointSecurityCodecTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/java/com/ainclusive/iotsim/domain/datasource/EndpointSecurityCodec.java \
        domain/src/test/java/com/ainclusive/iotsim/domain/datasource/EndpointSecurityCodecTest.java
git commit -m "feat(IS-131): EndpointSecurityCodec (validate, hash, redact)"
```

---

### Task 4: Proto `SecurityConfig` message + contract bump

**Files:**
- Modify: `worker-contract/src/main/proto/iotsim/worker/v1/protocol_data_source.proto`
- Modify: `worker-contract/src/main/java/com/ainclusive/iotsim/workercontract/WorkerContract.java:13`

**Interfaces:**
- Produces (generated): `com.ainclusive.iotsim.workercontract.v1.SecurityConfig` with `getAnonymousAllowed()`, `getUsernameEnabled()`, `getUsersList()`, `getUsersCount()`, builder `setAnonymousAllowed/​setUsernameEnabled/​addUsers`; `com.ainclusive.iotsim.workercontract.v1.UserCredential` with `getUsername()`, `getPasswordHash()`; `ConfigureRequest.getSecurityConfig()` / builder `setSecurityConfig(...)`. `WorkerContract.VERSION = "1.3.0"`.

- [ ] **Step 1: Edit the proto — extend `ConfigureRequest` and add messages**

In `ConfigureRequest` add field 4:

```proto
message ConfigureRequest {
  Schema schema = 1;         // protocol-neutral schema the worker projects
  int32 listen_port = 2;     // protocol port the Edge Device connects to (0 = ephemeral)
  map<string, string> options = 3;
  SecurityConfig security_config = 4;  // endpoint auth; absent/empty = None + Anonymous (IS-131)
}

// Simulated OPC UA endpoint security (IS-131). Empty message = the historical
// None/Anonymous server. Passwords arrive pre-hashed (never plaintext on the wire).
message SecurityConfig {
  bool anonymous_allowed = 1;
  bool username_enabled = 2;
  repeated UserCredential users = 3;
  // message security (SecurityPolicy/MessageSecurityMode) added in IS-132
}

message UserCredential {
  string username = 1;
  string password_hash = 2;  // salted PBKDF2 hash; the worker verifies against it
}
```

- [ ] **Step 2: Bump the contract version**

In `WorkerContract.java`, replace the version block:

```java
    // 1.3.0 adds the additive SecurityConfig on ConfigureRequest (simulated OPC UA
    // endpoint auth, IS-131); 1.2.0 added Capture (IS-045); 1.1.0 added
    // TestConnection/Scan (IS-043). The major is unchanged so existing workers stay compatible.
    public static final String VERSION = "1.3.0";
```

- [ ] **Step 3: Regenerate stubs and verify compile**

Run: `./gradlew :worker-contract:build`
Expected: BUILD SUCCESSFUL; generated `SecurityConfig`/`UserCredential` classes appear under `worker-contract/build/generated/sources/proto/main/java/com/ainclusive/iotsim/workercontract/v1/`.

- [ ] **Step 4: Commit**

```bash
git add worker-contract/src/main/proto/iotsim/worker/v1/protocol_data_source.proto \
        worker-contract/src/main/java/com/ainclusive/iotsim/workercontract/WorkerContract.java
git commit -m "feat(IS-131): add SecurityConfig to ConfigureRequest; contract 1.2.0->1.3.0"
```

---

### Task 5: Worker enforces username/password (OpcUaServerRuntime + service)

**Files:**
- Create: `workers/worker-opcua/src/main/java/com/ainclusive/iotsim/worker/opcua/AuthConfig.java`
- Modify: `workers/worker-opcua/src/main/java/com/ainclusive/iotsim/worker/opcua/OpcUaServerRuntime.java`
- Modify: `workers/worker-opcua/src/main/java/com/ainclusive/iotsim/worker/opcua/OpcUaProtocolService.java:83-101`
- Test: `workers/worker-opcua/src/test/java/com/ainclusive/iotsim/worker/opcua/OpcUaServerAuthIT.java`

**Interfaces:**
- Consumes: `PasswordHash.matches` (Task 1); generated `SecurityConfig`/`UserCredential` (Task 4).
- Produces: `record AuthConfig(boolean anonymousAllowed, boolean usernameEnabled, Map<String,String> userPasswordHashes)` with `static AuthConfig anonymous()`; `OpcUaServerRuntime` constructor `(int port, String bindAddress, String advertisedHost, List<VarDef> variables, AuthConfig auth, Consumer<ClientEvent>, Consumer<RuntimeEvent>)`.

- [ ] **Step 1: Write the failing test (worker IT)**

```java
package com.ainclusive.iotsim.worker.opcua;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.protocolmodel.PasswordHash;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.junit.jupiter.api.Test;

/** IS-131: the simulated server advertises UserName policy and validates credentials. */
class OpcUaServerAuthIT {

    @Test
    void usernameOnlyServerAcceptsValidAndRejectsInvalid() throws Exception {
        int port = freePort();
        AuthConfig auth = new AuthConfig(false, true, Map.of("operator", PasswordHash.encode("s3cret")));
        OpcUaServerRuntime runtime = new OpcUaServerRuntime(
                port, "127.0.0.1", "127.0.0.1",
                List.of(new VarDef("temp", "Temperature", "FLOAT64")), auth, e -> {}, e -> {});
        runtime.start();
        try {
            OpcUaClient ok = OpcUaClientSupport.connect(runtime.endpointUrl(), "PASSWORD", "operator", "s3cret");
            ok.disconnect().get(10, SECONDS);

            assertThatThrownBy(() ->
                    OpcUaClientSupport.connect(runtime.endpointUrl(), "PASSWORD", "operator", "wrong"));
            assertThatThrownBy(() ->
                    OpcUaClientSupport.connect(runtime.endpointUrl(), "ANONYMOUS", null, null));
        } finally {
            runtime.stop();
        }
    }

    @Test
    void anonymousStillWorksByDefault() throws Exception {
        int port = freePort();
        OpcUaServerRuntime runtime = new OpcUaServerRuntime(
                port, "127.0.0.1", "127.0.0.1",
                List.of(new VarDef("temp", "Temperature", "FLOAT64")), AuthConfig.anonymous(), e -> {}, e -> {});
        runtime.start();
        try {
            OpcUaClient client = OpcUaClientSupport.connect(runtime.endpointUrl(), "ANONYMOUS", null, null);
            assertThat(client).isNotNull();
            client.disconnect().get(10, SECONDS);
        } finally {
            runtime.stop();
        }
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            return socket.getLocalPort();
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run (Colima env exported): `./gradlew :workers:worker-opcua:test --tests '*OpcUaServerAuthIT'`
Expected: FAIL — the `OpcUaServerRuntime(..., AuthConfig, ...)` constructor and `AuthConfig` do not exist (compile error).

- [ ] **Step 3a: Create `AuthConfig`**

```java
package com.ainclusive.iotsim.worker.opcua;

import java.util.Map;

/** Worker-local view of the endpoint's accepted user tokens (from the proto SecurityConfig). */
record AuthConfig(boolean anonymousAllowed, boolean usernameEnabled, Map<String, String> userPasswordHashes) {

    AuthConfig {
        userPasswordHashes = userPasswordHashes == null ? Map.of() : Map.copyOf(userPasswordHashes);
    }

    static AuthConfig anonymous() {
        return new AuthConfig(true, false, Map.of());
    }
}
```

- [ ] **Step 3b: Add auth to `OpcUaServerRuntime`**

Add imports:

```java
import com.ainclusive.iotsim.protocolmodel.PasswordHash;
import java.util.ArrayList;
import org.eclipse.milo.opcua.sdk.server.identity.UsernameIdentityValidator;
import org.eclipse.milo.opcua.stack.core.types.structured.UserTokenPolicy;
```

Change the existing delegating constructors to pass `AuthConfig.anonymous()`, and thread `AuthConfig` through the main constructor. Replace the four existing constructors' delegation so the widest one takes `auth`:

```java
    OpcUaServerRuntime(int port, List<VarDef> variables) {
        this(port, "127.0.0.1", "127.0.0.1", variables, AuthConfig.anonymous(), event -> {}, event -> {});
    }

    OpcUaServerRuntime(int port, List<VarDef> variables, Consumer<ClientEvent> clientEventSink) {
        this(port, "127.0.0.1", "127.0.0.1", variables, AuthConfig.anonymous(), clientEventSink, event -> {});
    }

    OpcUaServerRuntime(int port, List<VarDef> variables, Consumer<ClientEvent> clientEventSink,
            Consumer<RuntimeEvent> runtimeEventSink) {
        this(port, "127.0.0.1", "127.0.0.1", variables, AuthConfig.anonymous(), clientEventSink, runtimeEventSink);
    }

    OpcUaServerRuntime(int port, String bindAddress, String advertisedHost, List<VarDef> variables,
            AuthConfig auth, Consumer<ClientEvent> clientEventSink, Consumer<RuntimeEvent> runtimeEventSink) {
```

Inside that main constructor, replace the single-policy endpoint builder line
`.addTokenPolicies(OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS)` with the computed set, and add the identity validator to the server config. Concretely, build the policy list before the `EndpointConfiguration` builder:

```java
            List<UserTokenPolicy> tokenPolicies = new ArrayList<>();
            if (auth.anonymousAllowed()) {
                tokenPolicies.add(OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS);
            }
            if (auth.usernameEnabled()) {
                tokenPolicies.add(OpcUaServerConfig.USER_TOKEN_POLICY_USERNAME);
            }
            if (tokenPolicies.isEmpty()) {
                tokenPolicies.add(OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS);
            }
```

Change the endpoint builder call to:

```java
                    .addTokenPolicies(tokenPolicies.toArray(new UserTokenPolicy[0]))
```

Add to the `OpcUaServerConfig.builder()` chain (e.g. after `.setProductUri(...)`):

```java
                    .setIdentityValidator(new UsernameIdentityValidator(
                            auth.anonymousAllowed(), challenge -> authenticate(auth, challenge)))
```

Add the helper method to the class:

```java
    /** True when the challenge names a configured user whose password hash matches. */
    private static boolean authenticate(AuthConfig auth,
            UsernameIdentityValidator.AuthenticationChallenge challenge) {
        if (!auth.usernameEnabled()) {
            return false;
        }
        String hash = auth.userPasswordHashes().get(challenge.getUsername());
        return hash != null && PasswordHash.matches(challenge.getPassword(), hash);
    }
```

- [ ] **Step 3c: Map the proto in `OpcUaProtocolService.configure`**

Add imports:

```java
import com.ainclusive.iotsim.workercontract.v1.SecurityConfig;
import com.ainclusive.iotsim.workercontract.v1.UserCredential;
import java.util.HashMap;
```

In `configure(...)`, after building `variables`/`bindAddress`/`advertisedHost`, build the `AuthConfig` and pass it to the runtime:

```java
        AuthConfig auth = toAuthConfig(request.getSecurityConfig());
        serverRuntime.set(new OpcUaServerRuntime(
                request.getListenPort(), bindAddress, advertisedHost, variables, auth,
                clientEventHub::emit, runtimeEventHub::emit));
```

Add the mapper (empty/default proto → anonymous, preserving backward compatibility):

```java
    private static AuthConfig toAuthConfig(SecurityConfig sc) {
        if (sc == null
                || (!sc.getAnonymousAllowed() && !sc.getUsernameEnabled() && sc.getUsersCount() == 0)) {
            return AuthConfig.anonymous();
        }
        Map<String, String> users = new HashMap<>();
        for (UserCredential u : sc.getUsersList()) {
            users.put(u.getUsername(), u.getPasswordHash());
        }
        return new AuthConfig(sc.getAnonymousAllowed(), sc.getUsernameEnabled(), users);
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :workers:worker-opcua:test --tests '*OpcUaServerAuthIT' --tests '*OpcUaServerRuntimeIT'`
Expected: PASS (new auth IT green; the existing runtime IT still green via the anonymous-defaulting constructors).

- [ ] **Step 5: Commit**

```bash
git add workers/worker-opcua/src/main/java/com/ainclusive/iotsim/worker/opcua/AuthConfig.java \
        workers/worker-opcua/src/main/java/com/ainclusive/iotsim/worker/opcua/OpcUaServerRuntime.java \
        workers/worker-opcua/src/main/java/com/ainclusive/iotsim/worker/opcua/OpcUaProtocolService.java \
        workers/worker-opcua/src/test/java/com/ainclusive/iotsim/worker/opcua/OpcUaServerAuthIT.java
git commit -m "feat(IS-131): OPC UA worker enforces username/password auth"
```

---

### Task 6: DB migration + jOOQ regeneration

**Files:**
- Create: `persistence/src/main/resources/db/migration/V10__datasource_security_config.sql`

**Interfaces:**
- Produces: generated `DATA_SOURCES.SECURITY_CONFIG` jOOQ column field + `DataSourcesRecord.getSecurityConfig()/setSecurityConfig(...)`.

- [ ] **Step 1: Confirm the next free migration version**

Run: `ls persistence/src/main/resources/db/migration/`
Expected: highest is `V9__...`. Use `V10`. If a higher version merged first, pick the next free number per the repo's `flyway-migration` rules (invoke `/flyway-migration add_datasource_security_config`).

- [ ] **Step 2: Create the migration**

```sql
-- IS-131: per-source simulated OPC UA endpoint security (user-token policy + hashed
-- credentials). Empty '{}' reproduces the historical None/Anonymous server.
alter table data_sources
    add column security_config jsonb not null default '{}'::jsonb;
```

- [ ] **Step 3: Regenerate jOOQ and verify the field exists**

Run: `./gradlew :persistence:generateJooq`
Then: `grep -n "SECURITY_CONFIG" persistence/build/generated/jooq/com/ainclusive/iotsim/persistence/jooq/tables/DataSources.java`
Expected: a `public final TableField<DataSourcesRecord, JSONB> SECURITY_CONFIG` line is present.

- [ ] **Step 4: Commit**

```bash
git add persistence/src/main/resources/db/migration/V10__datasource_security_config.sql
git commit -m "feat(IS-131): data_sources.security_config column + jOOQ regen"
```

---

### Task 7: Persistence — `DataSourceRow` + repository

**Files:**
- Modify: `persistence/src/main/java/com/ainclusive/iotsim/persistence/datasource/DataSourceRow.java`
- Modify: `persistence/src/main/java/com/ainclusive/iotsim/persistence/datasource/DataSourceRepository.java`
- Modify: `persistence/src/main/java/com/ainclusive/iotsim/persistence/datasource/JooqDataSourceRepository.java`
- Test: `persistence/src/test/java/com/ainclusive/iotsim/persistence/datasource/DataSourceRepositoryIT.java`

**Interfaces:**
- Produces: `DataSourceRow.securityConfig()` (raw stored JSON, after `runtimeConfig`); `insert(..., String securityConfigJson, String createdBy)`; `update(..., String securityConfigJson, boolean enabled, long expectedVersion)`. `insert`/`update` store the JSON **as given** (no hashing — that is the domain's job; import passes already-hashed JSON).

- [ ] **Step 1: Write the failing test (add to `DataSourceRepositoryIT`)**

```java
    @Test
    void insertAndUpdatePersistSecurityConfig() {
        String projectId = newProject();
        String stored = "{\"userTokens\":{\"anonymous\":false,"
                + "\"username\":{\"enabled\":true,\"users\":[{\"username\":\"op\",\"passwordHash\":\"h\"}]}}}";
        DataSourceRow row = repository.insert(
                projectId, "S", "OPC_UA", "MANUAL", 4840, null, "{}", stored, "local");
        assertThat(row.securityConfig()).contains("passwordHash");

        DataSourceRow updated = repository.update(
                row.id(), row.name(), row.simulatorPort(), row.realDeviceEndpoint(),
                row.runtimeConfig(), "{}", row.enabled(), row.version()).orElseThrow();
        assertThat(updated.securityConfig()).isEqualTo("{}");
    }
```

> Note: match `newProject()` to the IT's existing project-setup helper; if the IT
> creates a project inline, mirror that. The `insert`/`update` argument order below
> is authoritative.

- [ ] **Step 2: Run test to verify it fails**

Run (Colima env exported): `./gradlew :persistence:test --tests '*DataSourceRepositoryIT'`
Expected: FAIL — `insert`/`update` do not accept a `securityConfig` argument; `securityConfig()` missing on the row.

- [ ] **Step 3a: Add the field to `DataSourceRow`** (after `runtimeConfig`)

```java
        String runtimeConfig,
        String securityConfig,
        boolean enabled,
```

- [ ] **Step 3b: Update the `DataSourceRepository` interface signatures**

```java
    DataSourceRow insert(String projectId, String name, String protocol, String basis,
            int simulatorPort, String realDeviceEndpoint, String runtimeConfigJson,
            String securityConfigJson, String createdBy);

    Optional<DataSourceRow> update(String id, String name, int simulatorPort,
            String realDeviceEndpoint, String runtimeConfigJson, String securityConfigJson,
            boolean enabled, long expectedVersion);
```

- [ ] **Step 3c: Update `JooqDataSourceRepository`**

`insert(...)` — add the parameter and the column set:

```java
    public DataSourceRow insert(String projectId, String name, String protocol, String basis,
            int simulatorPort, String realDeviceEndpoint, String runtimeConfigJson,
            String securityConfigJson, String createdBy) {
        DataSourcesRecord record = dsl.insertInto(DATA_SOURCES)
                .set(DATA_SOURCES.ID, Ids.newId())
                .set(DATA_SOURCES.PROJECT_ID, projectId)
                .set(DATA_SOURCES.NAME, name)
                .set(DATA_SOURCES.PROTOCOL, protocol)
                .set(DATA_SOURCES.BASIS, basis)
                .set(DATA_SOURCES.SIMULATOR_PORT, simulatorPort)
                .set(DATA_SOURCES.REAL_DEVICE_ENDPOINT, endpointToJsonb(realDeviceEndpoint))
                .set(DATA_SOURCES.RUNTIME_CONFIG, json(runtimeConfigJson))
                .set(DATA_SOURCES.SECURITY_CONFIG, json(securityConfigJson))
                .set(DATA_SOURCES.CREATED_BY, createdBy)
                .returning()
                .fetchOne();
        return map(record);
    }
```

`update(...)` — add the parameter and the column set:

```java
    public Optional<DataSourceRow> update(String id, String name, int simulatorPort,
            String realDeviceEndpoint, String runtimeConfigJson, String securityConfigJson,
            boolean enabled, long expectedVersion) {
        DataSourcesRecord record = dsl.update(DATA_SOURCES)
                .set(DATA_SOURCES.NAME, name)
                .set(DATA_SOURCES.SIMULATOR_PORT, simulatorPort)
                .set(DATA_SOURCES.REAL_DEVICE_ENDPOINT, endpointToJsonb(realDeviceEndpoint))
                .set(DATA_SOURCES.RUNTIME_CONFIG, json(runtimeConfigJson))
                .set(DATA_SOURCES.SECURITY_CONFIG, json(securityConfigJson))
                .set(DATA_SOURCES.ENABLED, enabled)
                .set(DATA_SOURCES.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .set(DATA_SOURCES.VERSION, DATA_SOURCES.VERSION.plus(1))
                .where(DATA_SOURCES.ID.eq(id).and(DATA_SOURCES.VERSION.eq(expectedVersion)))
                .returning()
                .fetchOne();
        return Optional.ofNullable(record).map(this::map);
    }
```

`duplicate(...)` — copy the column on the insert:

```java
                .set(DATA_SOURCES.RUNTIME_CONFIG, source.getRuntimeConfig())
                .set(DATA_SOURCES.SECURITY_CONFIG, source.getSecurityConfig())
```

`map(...)` — read the column (after `runtimeConfig`):

```java
                jsonString(r.getRuntimeConfig()),
                jsonString(r.getSecurityConfig()),
                Boolean.TRUE.equals(r.getEnabled()),
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :persistence:test --tests '*DataSourceRepositoryIT'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add persistence/src/main/java/com/ainclusive/iotsim/persistence/datasource/ \
        persistence/src/test/java/com/ainclusive/iotsim/persistence/datasource/DataSourceRepositoryIT.java
git commit -m "feat(IS-131): persist data-source security_config"
```

---

### Task 8: Domain — `DataSource` field + service create/update (hash + redact)

**Files:**
- Modify: `domain/src/main/java/com/ainclusive/iotsim/domain/datasource/DataSource.java`
- Modify: `domain/src/main/java/com/ainclusive/iotsim/domain/datasource/DataSourceService.java`
- Test: `domain/src/test/java/com/ainclusive/iotsim/domain/datasource/DataSourceServiceTest.java`

**Interfaces:**
- Consumes: `EndpointSecurityCodec` (Task 3); repository signatures (Task 7).
- Produces: `DataSource.securityConfig()` (**raw stored JSON with hashes** — the export path reads this; the API layer redacts it in Task 11). `DataSourceService.create(..., String securityConfig, ...)` and `update(..., String securityConfig, ...)` accept API-write JSON (plaintext), normalise+hash before persisting.

> Design note (do not deviate): the domain `DataSource.securityConfig()` carries the
> raw stored JSON **including hashes** so the exporter (which sees only domain objects)
> can round-trip them. Redaction happens once, at the API boundary (Task 11). Hashes
> are not plaintext secrets and are export-safe by decision (see spec §Password handling).

- [ ] **Step 1: Write the failing test (add to `DataSourceServiceTest`)**

```java
    @Test
    void createHashesSecurityConfigPassword() {
        // mirror the existing test setup for project + service under test
        String write = "{\"userTokens\":{\"anonymous\":false,\"username\":{\"enabled\":true,"
                + "\"users\":[{\"username\":\"operator\",\"password\":\"s3cret\"}]}}}";
        DataSource ds = service.create(projectId, "Auth Source", "OPC_UA", "MANUAL",
                4840, null, "{}", write, null, null, "local");
        assertThat(ds.securityConfig()).contains("passwordHash").doesNotContain("s3cret");
    }
```

> Adapt `projectId`/`service` to the test class's existing fixtures. The `create`
> argument order is authoritative: `(projectId, name, protocol, basis, simulatorPort,
> realDeviceEndpoint, runtimeConfig, securityConfig, connectionCredentials, initialNodes, actor)`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :domain:test --tests '*DataSourceServiceTest'`
Expected: FAIL — `create` does not accept a `securityConfig` argument.

- [ ] **Step 3a: Add the field to `DataSource`** (after `runtimeConfig`)

```java
        String runtimeConfig,
        String securityConfig,
        boolean enabled,
```

- [ ] **Step 3b: Thread `securityConfig` through `create`**

Change the signature and hash before insert:

```java
    public DataSource create(String projectId, String name, String protocol, String basis,
            Integer simulatorPort, String realDeviceEndpoint, String runtimeConfig, String securityConfig,
            ConnectionCredentials connectionCredentials, List<SchemaNode> initialNodes, String actor) {
        requireProject(projectId);
        Protocol parsedProtocol = Protocol.valueOf(protocol);
        SourceBasis.valueOf(basis);
        requireValidJson(runtimeConfig, "runtimeConfig");
        String storedSecurity = EndpointSecurityCodec.normalizeForStorage(securityConfig);
        int port = simulatorPort != null
                ? validatePort(simulatorPort)
                : SimulatorUrl.defaultPort(parsedProtocol);
        DataSourceRow row = dataSources.insert(
                projectId, name, protocol, basis, port, realDeviceEndpoint, runtimeConfig,
                storedSecurity, actor);
        applyCredentials(row.id(), connectionCredentials);
        ...
```

- [ ] **Step 3c: Thread `securityConfig` through `update`**

```java
    public DataSource update(String projectId, String id, String name, Integer simulatorPort,
            String realDeviceEndpoint, String runtimeConfig, String securityConfig, Boolean enabled,
            ConnectionCredentials connectionCredentials, long expectedVersion) {
        DataSourceRow existing = requireRow(projectId, id);
        requireValidJson(runtimeConfig, "runtimeConfig");
        String newName = name != null ? name : existing.name();
        int newPort = simulatorPort != null ? validatePort(simulatorPort) : existing.simulatorPort();
        String newEndpoint = realDeviceEndpoint != null ? realDeviceEndpoint : existing.realDeviceEndpoint();
        String newRuntimeConfig = runtimeConfig != null ? runtimeConfig : existing.runtimeConfig();
        // null leaves the persisted security config unchanged; an explicit value is normalised + hashed.
        String newSecurity = securityConfig != null
                ? EndpointSecurityCodec.normalizeForStorage(securityConfig)
                : existing.securityConfig();
        boolean newEnabled = enabled != null ? enabled : existing.enabled();
        DataSourceRow updated = dataSources.update(
                id, newName, newPort, newEndpoint, newRuntimeConfig, newSecurity, newEnabled, expectedVersion)
                .orElseThrow(() -> new ConcurrencyConflictException("DataSource", id, expectedVersion));
        applyCredentials(id, connectionCredentials);
        return map(updated);
    }
```

- [ ] **Step 3d: Populate the field in `map(...)`** (raw stored JSON, after `runtimeConfig`)

```java
                r.runtimeConfig(),
                r.securityConfig(),
                r.enabled(),
```

- [ ] **Step 3e: Fix the three other in-module callers** (the new params broke them at compile time)

These are the only domain callers besides `DataSourceController` (Task 11) and `ProjectImportService` (Task 12). Apply each edit exactly:

1. `domain/.../scan/ScanService.java:161` — add `null` for `securityConfig` (scan-created sources are anonymous):

```java
        DataSource created = dataSources.create(
                projectId, name, job.protocol(), "SCAN", null, endpoint, null, null, null, null, actor);
```

2. `domain/.../synthetic/SyntheticSourceService.java:44` — add `null` for `securityConfig`:

```java
        DataSource created = dataSources.create(
                projectId, name, protocol, "SYNTHETIC", simulatorPort, null,
                json.writeValueAsString(config), null, null, null, actor);
```

3. `domain/.../project/ProjectService.java:111` — this calls the **repository** `insert` directly (project duplicate), so pass `ds.securityConfig()` (copy the auth config to the duplicate) before `ds.createdBy()`:

```java
            DataSourceRow newDs = dataSources.insert(
                    copy.id(), ds.name(), ds.protocol(), ds.basis(),
                    ds.simulatorPort(), ds.realDeviceEndpoint(), ds.runtimeConfig(),
                    ds.securityConfig(), ds.createdBy());
```

> `DataSourceService.duplicate(...)` needs no change: it calls the repository's
> `duplicate(...)`, which copies `SECURITY_CONFIG` (done in Task 7 Step 3c).

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :domain:test`
Expected: PASS (new test green; existing domain tests compile with the extra argument).

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/java/com/ainclusive/iotsim/domain/datasource/DataSource.java \
        domain/src/main/java/com/ainclusive/iotsim/domain/datasource/DataSourceService.java \
        domain/src/test/java/com/ainclusive/iotsim/domain/datasource/DataSourceServiceTest.java
git commit -m "feat(IS-131): DataSourceService hashes + carries security config"
```

---

### Task 9: `RuntimeStartSpec` carries `EndpointSecurity`

**Files:**
- Modify: `platform/src/main/java/com/ainclusive/iotsim/platform/runtime/RuntimeStartSpec.java`
- Modify: `domain/src/main/java/com/ainclusive/iotsim/domain/datasource/RuntimeStartSpecs.java`
- Test: `domain/src/test/java/com/ainclusive/iotsim/domain/datasource/RuntimeStartSpecsTest.java`

**Interfaces:**
- Consumes: `EndpointSecurity` (Task 2), `EndpointSecurityCodec.toModel` (Task 3), `DataSourceRow.securityConfig()` (Task 7).
- Produces: `RuntimeStartSpec.endpointSecurity()` (never null — defaults to `EndpointSecurity.none()`). Existing constructors keep working (delegate with `none()`).

- [ ] **Step 1: Write the failing test (add to `RuntimeStartSpecsTest`)**

```java
    @Test
    void buildsEndpointSecurityFromRow() {
        String stored = "{\"userTokens\":{\"anonymous\":false,"
                + "\"username\":{\"enabled\":true,\"users\":[{\"username\":\"op\",\"passwordHash\":\"h\"}]}}}";
        DataSourceRow row = rowWithSecurityConfig(stored);   // mirror the test's row-builder helper
        RuntimeStartSpec spec = RuntimeStartSpecs.of(schemas, row);
        assertThat(spec.endpointSecurity().usernameEnabled()).isTrue();
        assertThat(spec.endpointSecurity().anonymousAllowed()).isFalse();
        assertThat(spec.endpointSecurity().users()).singleElement()
                .satisfies(u -> assertThat(u.username()).isEqualTo("op"));
    }
```

> Build the `DataSourceRow` the way the existing test does, setting `securityConfig`
> to `stored`. If the test has no row helper, construct `DataSourceRow` directly with
> the new `securityConfig` field.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :domain:test --tests '*RuntimeStartSpecsTest'`
Expected: FAIL — `spec.endpointSecurity()` does not exist.

- [ ] **Step 3a: Add the field to `RuntimeStartSpec`**

```java
public record RuntimeStartSpec(
        String protocol,
        int schemaVersion,
        List<SchemaNode> schemaNodes,
        int listenPort,
        DeterministicSettings deterministicSettings,
        EndpointSecurity endpointSecurity) {

    public RuntimeStartSpec {
        schemaNodes = schemaNodes == null ? List.of() : List.copyOf(schemaNodes);
        endpointSecurity = endpointSecurity == null ? EndpointSecurity.none() : endpointSecurity;
    }

    /** Convenience constructor for callers that do not supply deterministic settings. */
    public RuntimeStartSpec(String protocol, int schemaVersion, List<SchemaNode> schemaNodes, int listenPort) {
        this(protocol, schemaVersion, schemaNodes, listenPort, null, EndpointSecurity.none());
    }

    /** Convenience constructor without endpoint security (defaults to None/Anonymous). */
    public RuntimeStartSpec(String protocol, int schemaVersion, List<SchemaNode> schemaNodes, int listenPort,
            DeterministicSettings deterministicSettings) {
        this(protocol, schemaVersion, schemaNodes, listenPort, deterministicSettings, EndpointSecurity.none());
    }
}
```

Add `import com.ainclusive.iotsim.platform.runtime.EndpointSecurity;` (same package — no import needed since both are in `platform.runtime`).

- [ ] **Step 3b: Build it in `RuntimeStartSpecs.of`**

```java
    public static RuntimeStartSpec of(SchemaRepository schemas, DataSourceRow source,
            DeterministicSettings deterministicSettings) {
        var current = schemas.findCurrent(source.id());
        return new RuntimeStartSpec(
                source.protocol(),
                current.map(SchemaWithNodes::version).orElse(0),
                current.map(SchemaWithNodes::nodes).orElse(List.of()),
                source.simulatorPort(),
                deterministicSettings,
                EndpointSecurityCodec.toModel(source.securityConfig()));
    }
```

Add imports: `com.ainclusive.iotsim.platform.runtime.EndpointSecurity` is not needed here; `EndpointSecurityCodec` is same-package.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :platform:test :domain:test`
Expected: PASS. Existing supervisor/app tests that construct `RuntimeStartSpec` with 4–5 args still compile (convenience constructors preserved).

- [ ] **Step 5: Commit**

```bash
git add platform/src/main/java/com/ainclusive/iotsim/platform/runtime/RuntimeStartSpec.java \
        domain/src/main/java/com/ainclusive/iotsim/domain/datasource/RuntimeStartSpecs.java \
        domain/src/test/java/com/ainclusive/iotsim/domain/datasource/RuntimeStartSpecsTest.java
git commit -m "feat(IS-131): RuntimeStartSpec carries EndpointSecurity"
```

---

### Task 10: Supervisor — map `EndpointSecurity` → proto and send it

**Files:**
- Modify: `runtime-supervisor/src/main/java/com/ainclusive/iotsim/supervisor/WorkerClient.java:65-77`
- Modify: `runtime-supervisor/src/main/java/com/ainclusive/iotsim/supervisor/Supervisor.java` (`ManagedWorker` configure call, ~line 763)
- Test: `runtime-supervisor/src/test/java/com/ainclusive/iotsim/supervisor/WorkerClientConfigureTest.java`

**Interfaces:**
- Consumes: proto `SecurityConfig`/`UserCredential` (Task 4); `RuntimeStartSpec.endpointSecurity()` (Task 9).
- Produces: `WorkerClient.configure(Schema, int listenPort, String bindAddress, String advertisedHost, SecurityConfig securityConfig)`; `buildConfigureRequest(..., SecurityConfig)` sets `security_config`.

- [ ] **Step 1: Write the failing test (extend `WorkerClientConfigureTest`)**

```java
    @Test
    void configureRequestCarriesSecurityConfig() {
        com.ainclusive.iotsim.workercontract.v1.SecurityConfig sc =
                com.ainclusive.iotsim.workercontract.v1.SecurityConfig.newBuilder()
                        .setUsernameEnabled(true)
                        .addUsers(com.ainclusive.iotsim.workercontract.v1.UserCredential.newBuilder()
                                .setUsername("op").setPasswordHash("h"))
                        .build();
        ConfigureRequest req = WorkerClient.buildConfigureRequest(
                Schema.newBuilder().build(), 4840, "0.0.0.0", "plant.local", sc);
        assertThat(req.getSecurityConfig().getUsernameEnabled()).isTrue();
        assertThat(req.getSecurityConfig().getUsers(0).getUsername()).isEqualTo("op");
    }
```

Also update the existing `configureRequestCarriesBindAndAdvertisedOptions` test's `buildConfigureRequest(...)` call to pass `SecurityConfig.getDefaultInstance()` as the new last argument.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :runtime-supervisor:test --tests '*WorkerClientConfigureTest'`
Expected: FAIL — `buildConfigureRequest` has no 5-arg overload.

- [ ] **Step 3a: Update `WorkerClient`**

Add import `com.ainclusive.iotsim.workercontract.v1.SecurityConfig;` and change:

```java
    public Ack configure(Schema schema, int listenPort, String bindAddress, String advertisedHost,
            SecurityConfig securityConfig) {
        return stub.configure(buildConfigureRequest(schema, listenPort, bindAddress, advertisedHost, securityConfig));
    }

    static ConfigureRequest buildConfigureRequest(Schema schema, int listenPort,
            String bindAddress, String advertisedHost, SecurityConfig securityConfig) {
        return ConfigureRequest.newBuilder()
                .setSchema(schema)
                .setListenPort(listenPort)
                .putOptions("bindAddress", bindAddress)
                .putOptions("advertisedHost", advertisedHost)
                .setSecurityConfig(securityConfig)
                .build();
    }
```

- [ ] **Step 3b: Update `Supervisor.ManagedWorker`**

Add imports:

```java
import com.ainclusive.iotsim.platform.runtime.EndpointSecurity;
import com.ainclusive.iotsim.workercontract.v1.SecurityConfig;
import com.ainclusive.iotsim.workercontract.v1.UserCredential;
```

Change the `configure` call (~line 763) to pass the mapped proto:

```java
                newClient.configure(
                        toProtoSchema(spec.schemaVersion(), spec.schemaNodes()), spec.listenPort(),
                        network.bindAddress(), network.advertisedHost(),
                        toProtoSecurityConfig(spec.endpointSecurity()));
```

Add the mapper (as a `private static` method in `Supervisor`, near `toProtoSchema`):

```java
    private static SecurityConfig toProtoSecurityConfig(EndpointSecurity sec) {
        if (sec == null) {
            return SecurityConfig.getDefaultInstance();
        }
        SecurityConfig.Builder b = SecurityConfig.newBuilder()
                .setAnonymousAllowed(sec.anonymousAllowed())
                .setUsernameEnabled(sec.usernameEnabled());
        for (EndpointSecurity.UserCredential u : sec.users()) {
            b.addUsers(UserCredential.newBuilder()
                    .setUsername(u.username())
                    .setPasswordHash(u.passwordHash()));
        }
        return b.build();
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :runtime-supervisor:test`
Expected: PASS (config test green; other supervisor tests still compile — they build `RuntimeStartSpec` via preserved constructors, so `endpointSecurity()` returns `none()` → default proto).

- [ ] **Step 5: Commit**

```bash
git add runtime-supervisor/src/main/java/com/ainclusive/iotsim/supervisor/WorkerClient.java \
        runtime-supervisor/src/main/java/com/ainclusive/iotsim/supervisor/Supervisor.java \
        runtime-supervisor/src/test/java/com/ainclusive/iotsim/supervisor/WorkerClientConfigureTest.java
git commit -m "feat(IS-131): supervisor sends SecurityConfig to the worker"
```

---

### Task 11: API — request/response DTOs (accept plaintext, return redacted)

**Files:**
- Modify: `api/src/main/java/com/ainclusive/iotsim/api/datasource/DataSourceController.java`
- Test: extend `domain` codec coverage (redaction) — no api-layer test exists; assert redaction via the response mapping helper.

**Interfaces:**
- Consumes: `DataSourceService.create/update` (Task 8, now with `securityConfig`); `DataSource.securityConfig()` raw (Task 8).
- Produces: `CreateDataSourceRequest.securityConfig()`, `UpdateDataSourceRequest.securityConfig()` (API-write JSON, plaintext passwords); `DataSourceResponse.securityConfig` (redacted — no `passwordHash`).

> The controller must NOT return the raw hashed JSON. Redact at the boundary using
> the same codec. Because `EndpointSecurityCodec` is package-private in `domain`, add a
> **public** redaction entry point the API can call.

- [ ] **Step 1a: Expose a public redactor from the domain**

Add to `DataSourceService` (domain) a thin public method delegating to the codec:

```java
    /** API-safe view of a stored security config (drops password hashes). */
    public String redactSecurityConfig(String storedJson) {
        return EndpointSecurityCodec.redact(storedJson);
    }
```

- [ ] **Step 1b: Write the failing test (add to `DataSourceServiceTest`)**

```java
    @Test
    void redactSecurityConfigDropsHashes() {
        String stored = "{\"userTokens\":{\"anonymous\":false,\"username\":{\"enabled\":true,"
                + "\"users\":[{\"username\":\"op\",\"passwordHash\":\"h\"}]}}}";
        String redacted = service.redactSecurityConfig(stored);
        assertThat(redacted).contains("op").doesNotContain("passwordHash").doesNotContain("\"h\"");
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :domain:test --tests '*DataSourceServiceTest'`
Expected: FAIL — `redactSecurityConfig` does not exist.

- [ ] **Step 3a: Implement the redactor** (Step 1a code) and re-run — Expected: PASS.

- [ ] **Step 3b: Wire the DTOs in `DataSourceController`**

Add `securityConfig` to the request records and pass through:

```java
    public record CreateDataSourceRequest(
            String name, String protocol, String basis, Integer simulatorPort,
            String realDeviceEndpoint, String runtimeConfig, String securityConfig,
            ConnectionConfigRequest connectionConfig, List<NodeDto> initialSchema) {}

    public record UpdateDataSourceRequest(
            String name, Integer simulatorPort, String realDeviceEndpoint, String runtimeConfig,
            String securityConfig, Boolean enabled, ConnectionConfigRequest connectionConfig) {}
```

`create(...)` call:

```java
        DataSource ds = dataSources.create(
                projectId, req.name(), req.protocol(), req.basis(),
                req.simulatorPort(), req.realDeviceEndpoint(), req.runtimeConfig(), req.securityConfig(),
                CredentialRequests.toCredentials(req.connectionConfig()), initialNodes, "local");
```

`update(...)` call:

```java
        DataSource ds = dataSources.update(
                projectId, id, req.name(), req.simulatorPort(), req.realDeviceEndpoint(),
                req.runtimeConfig(), req.securityConfig(), req.enabled(),
                CredentialRequests.toCredentials(req.connectionConfig()), parseVersion(ifMatch));
```

Response — redact. Change `DataSourceResponse` to add a `securityConfig` field and build it via the service redactor. Because `DataSourceResponse.from(DataSource)` is static and has no service access, redact in each handler and pass it in. Simplest: add the field and a 2-arg factory:

```java
    public record DataSourceResponse(
            String id, String projectId, String name, String protocol, String basis,
            String schemaId, Integer schemaVersion, int simulatorPort, String realDeviceEndpoint,
            String runtimeConfig, String securityConfig, boolean enabled, String runtimeState,
            String credentialState, String serveUrl, Instant createdAt, Instant updatedAt,
            String createdBy, long version) {

        public static DataSourceResponse from(DataSource d, String redactedSecurityConfig) {
            return new DataSourceResponse(
                    d.id(), d.projectId(), d.name(), d.protocol().name(), d.basis().name(),
                    d.schemaId(), d.schemaVersion(), d.simulatorPort(), d.realDeviceEndpoint(),
                    d.runtimeConfig(), redactedSecurityConfig, d.enabled(), d.runtimeState().name(),
                    d.credentialState().name(), d.serveUrl(), d.createdAt(), d.updatedAt(),
                    d.createdBy(), d.version());
        }
    }
```

Update every `DataSourceResponse.from(ds)` call in the controller to
`DataSourceResponse.from(ds, dataSources.redactSecurityConfig(ds.securityConfig()))`, and the `list` mapping:

```java
        return dataSources.listPaged(projectId, protocol, cursor, limit)
                .map(d -> DataSourceResponse.from(d, dataSources.redactSecurityConfig(d.securityConfig())));
```

- [ ] **Step 4: Run the API + domain builds**

Run: `./gradlew :domain:test :api:test`
Expected: PASS (adapt any existing api test that references `DataSourceResponse.from(ds)` or the record's field count).

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/java/com/ainclusive/iotsim/domain/datasource/DataSourceService.java \
        api/src/main/java/com/ainclusive/iotsim/api/datasource/DataSourceController.java \
        domain/src/test/java/com/ainclusive/iotsim/domain/datasource/DataSourceServiceTest.java
git commit -m "feat(IS-131): API accepts security config, returns redacted view"
```

---

### Task 12: Export / import round-trip

**Files:**
- Modify: `domain/src/main/java/com/ainclusive/iotsim/domain/io/ProjectZipExporter.java` (`dataSourceDto`, ~line 133)
- Modify: `domain/src/main/java/com/ainclusive/iotsim/domain/io/ProjectImportService.java` (~lines 140-151)
- Test: `domain/src/test/java/com/ainclusive/iotsim/domain/io/ProjectImportServiceTest.java` (and/or `ProjectZipExporterTest`)

**Interfaces:**
- Consumes: `DataSource.securityConfig()` raw (Task 8); repository `insert`/`update` with `securityConfigJson` (Task 7).
- Produces: exported `securityConfig` key (hashes preserved); import restores it as-is (no re-hash — it is already hashed).

- [ ] **Step 1: Write the failing test (add to `ProjectImportServiceTest`)**

```java
    @Test
    void importPreservesSecurityConfigHashes() {
        // Build an export bundle whose data-sources.json entry carries a hashed securityConfig,
        // mirroring the test's existing bundle-building helper, then import it.
        String stored = "{\"userTokens\":{\"anonymous\":false,\"username\":{\"enabled\":true,"
                + "\"users\":[{\"username\":\"op\",\"passwordHash\":\"pbkdf2-sha256$1$aa$bb\"}]}}}";
        // ... import a bundle whose data source has "securityConfig": stored ...
        DataSourceRow imported = /* look up the imported row */ ;
        assertThat(imported.securityConfig()).contains("passwordHash");
    }
```

> Follow the existing test's mechanics for constructing/reading a bundle. The key
> assertion: the imported row's `securityConfig` equals the exported (hashed) JSON.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :domain:test --tests '*ProjectImportServiceTest'`
Expected: FAIL — import does not carry `securityConfig` (and the `insert` signature changed, so the current import call will not even compile until Step 3b).

- [ ] **Step 3a: Export the field** — in `ProjectZipExporter.dataSourceDto`, add:

```java
        m.put("runtimeConfig", ds.runtimeConfig());
        m.put("securityConfig", ds.securityConfig());  // hashed; part of the simulation (IS-131)
```

- [ ] **Step 3b: Import the field** — in `ProjectImportService`, read it and pass to the repository calls (which changed signature in Task 7):

```java
                    String runtimeConfig = ds.path("runtimeConfig").asString(null);
                    String securityConfig = ds.path("securityConfig").asString(null);
                    boolean enabled = ds.path("enabled").asBoolean(false);

                    DataSourceRow row = dataSources.insert(
                            newProject.id(), dsName, protocol, basis, simulatorPort, realDeviceEndpoint,
                            runtimeConfig, securityConfig, actor);

                    if (enabled) {
                        dataSources.update(row.id(), row.name(), row.simulatorPort(),
                                row.realDeviceEndpoint(), row.runtimeConfig(), row.securityConfig(),
                                true, row.version());
                    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :domain:test --tests '*ProjectImportServiceTest' --tests '*ProjectZipExporterTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/java/com/ainclusive/iotsim/domain/io/ProjectZipExporter.java \
        domain/src/main/java/com/ainclusive/iotsim/domain/io/ProjectImportService.java \
        domain/src/test/java/com/ainclusive/iotsim/domain/io/ProjectImportServiceTest.java
git commit -m "feat(IS-131): export/import round-trips security config (hashed)"
```

---

### Task 13: Governance docs + full-build verification

**Files:**
- Modify: `backend-specs/08_AUTH_AND_MODES.md`
- Modify: `backend-specs/02_WORKER_CONTRACT_AND_IPC.md`
- Modify: `SPEC.md`
- Modify: `docs/superpowers/specs/2026-07-02-is-130-opcua-endpoint-security-design.md` (mark IS-131 slice done if desired)

> Governance files require explicit user approval to edit (`AGENTS.md`). Propose the
> exact wording and get confirmation before committing this task's doc edits.

- [ ] **Step 1: `08_AUTH_AND_MODES.md`** — under "Secrets & PKI", add a clarifying bullet:

```markdown
- The **accepted credentials of a simulated data source** (the username/password an
  Edge Device must present to a simulated OPC UA server) are part of the data-source
  definition, not a real-source connection secret: stored as a salted hash in
  `data_sources.security_config`, exported with the project, and never kept in
  plaintext. This is distinct from scan/record connection secrets above, which stay
  session-only.
```

- [ ] **Step 2: `02_WORKER_CONTRACT_AND_IPC.md`** — document the additive field:

```markdown
- `ConfigureRequest.security_config` (`SecurityConfig`, added in contract `1.3.0`,
  IS-131): the simulated OPC UA endpoint's accepted user tokens (anonymous /
  username+password, passwords pre-hashed). An empty message = None/Anonymous, so
  older workers and unset configs behave exactly as before.
```

- [ ] **Step 3: `SPEC.md`** — extend the "Simulate OPC UA Data Sources" capability Explanation with one sentence (propose to user first):

```markdown
Simulated servers can run without authentication or require username/password
authentication, mirroring how a real OPC UA server exposes its security.
```

- [ ] **Step 4: Full build (Definition of Done)**

Run (Colima env exported so ITs execute):
```bash
export DOCKER_HOST=... TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=...
./gradlew build
```
Expected: BUILD SUCCESSFUL — all modules, including `worker-opcua` auth IT, `persistence` repository IT, and the app-level `OpcUaWorkerProcessIT`, green. Spotless/Checkstyle clean (no unused imports).

- [ ] **Step 5: Commit**

```bash
git add backend-specs/08_AUTH_AND_MODES.md backend-specs/02_WORKER_CONTRACT_AND_IPC.md SPEC.md \
        docs/superpowers/specs/2026-07-02-is-130-opcua-endpoint-security-design.md
git commit -m "docs(IS-131): clarify simulated-server credential governance + contract"
```

> The TASKS.md checkbox flip and PR are handled by the repo's `/open-pr` skill, which
> flips the catalog checkbox in the same PR (the CI catalog-sync gate) and arms
> squash auto-merge. Do not hand-edit TASKS.md here.

---

## Notes for the implementer

- **Start the task the repo way first:** run `/start-task IS-131` (claims the board, creates the linked branch) BEFORE Task 1. The spec/plan docs get committed on that branch.
- **Commit the spec + this plan** as the first commit on the branch (they currently live uncommitted on `master`'s working tree):
  `git add docs/superpowers/specs/2026-07-02-is-130-opcua-endpoint-security-design.md docs/superpowers/plans/2026-07-02-is-130-opcua-username-auth.md && git commit -m "docs(IS-131): endpoint-security spec + implementation plan"`.
- **Argument-order discipline:** `DataSourceService.create` order is `(projectId, name, protocol, basis, simulatorPort, realDeviceEndpoint, runtimeConfig, securityConfig, connectionCredentials, initialNodes, actor)`; `update` inserts `securityConfig` right after `runtimeConfig`. `Repository.insert` puts `securityConfigJson` right after `runtimeConfigJson` and before `createdBy`; `update` puts it after `runtimeConfigJson` and before `enabled`. `DataSourceRow`/`DataSource` place `securityConfig` right after `runtimeConfig`.
- **Backward-compat guardrail:** after Tasks 4–5 and 9–10, the empty/default `SecurityConfig` path must keep every pre-existing worker/supervisor/app test green — that is the regression signal that None/Anonymous is preserved.
```
