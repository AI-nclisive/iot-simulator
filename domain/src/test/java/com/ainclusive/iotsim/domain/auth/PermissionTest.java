package com.ainclusive.iotsim.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PermissionTest {

    @Test
    void everyConstantHasANonBlankKey() {
        for (Permission p : Permission.values()) {
            assertThat(p.key()).as("key for %s", p).isNotBlank();
        }
    }

    @Test
    void keysAreDotScoped() {
        for (Permission p : Permission.values()) {
            assertThat(p.key()).as("key for %s must be dot-scoped", p).contains(".");
        }
    }

    @Test
    void keysAreUnique() {
        long distinctCount = java.util.Arrays.stream(Permission.values())
                .map(Permission::key)
                .distinct()
                .count();
        assertThat(distinctCount).isEqualTo(Permission.values().length);
    }

    @Test
    void fromKeyRoundTrip() {
        for (Permission p : Permission.values()) {
            assertThat(Permission.fromKey(p.key())).isEqualTo(p);
        }
    }

    @Test
    void fromKeyThrowsForUnknown() {
        assertThatThrownBy(() -> Permission.fromKey("no.such.permission"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no.such.permission");
    }

    @Test
    void knownPermissionsIncludeSpecMandatedOnes() {
        // Spot-check the permissions called out in 08_AUTH_AND_MODES.md.
        assertThat(Permission.fromKey("source.start")).isEqualTo(Permission.SOURCE_START);
        assertThat(Permission.fromKey("source.configure")).isEqualTo(Permission.SOURCE_CONFIGURE);
        assertThat(Permission.fromKey("evidence.export")).isEqualTo(Permission.EVIDENCE_EXPORT);
        assertThat(Permission.fromKey("admin.access")).isEqualTo(Permission.ADMIN_ACCESS);
    }
}
