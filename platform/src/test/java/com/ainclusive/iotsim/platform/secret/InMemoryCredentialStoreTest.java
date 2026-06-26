package com.ainclusive.iotsim.platform.secret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InMemoryCredentialStoreTest {

    private final InMemoryCredentialStore store = new InMemoryCredentialStore();

    @Test
    void putThenFindRoundTrips() {
        ConnectionCredentials creds = ConnectionCredentials.password("user", "pw");
        store.put("ds-1", creds);
        assertThat(store.has("ds-1")).isTrue();
        assertThat(store.find("ds-1")).contains(creds);
    }

    @Test
    void missingHandleIsEmpty() {
        assertThat(store.has("nope")).isFalse();
        assertThat(store.find("nope")).isEmpty();
    }

    @Test
    void putReplacesExisting() {
        store.put("ds-1", ConnectionCredentials.password("a", "1"));
        store.put("ds-1", ConnectionCredentials.externalRef("vault://key"));
        assertThat(store.find("ds-1")).map(ConnectionCredentials::mode)
                .contains(ConnectionCredentials.Mode.EXTERNAL_REF);
    }

    @Test
    void clearRemovesHeldCredentials() {
        store.put("ds-1", ConnectionCredentials.password("user", "pw"));
        store.clear("ds-1");
        assertThat(store.has("ds-1")).isFalse();
    }

    @Test
    void putRejectsNullHandleOrCredentials() {
        assertThatThrownBy(() -> store.put(null, ConnectionCredentials.anonymous()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.put("ds-1", null))
                .isInstanceOf(NullPointerException.class);
    }
}
