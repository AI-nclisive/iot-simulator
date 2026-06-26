package com.ainclusive.iotsim.platform.secret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ConnectionCredentialsTest {

    @Test
    void passwordRequiresASecret() {
        assertThatThrownBy(() -> ConnectionCredentials.password("user", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void externalRefRequiresAReference() {
        assertThatThrownBy(() -> ConnectionCredentials.externalRef(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anonymousCarriesNoSecret() {
        ConnectionCredentials creds = ConnectionCredentials.anonymous();
        assertThat(creds.hasSecret()).isFalse();
        assertThat(creds.secret()).isNull();
    }

    @Test
    void passwordCarriesItsSecretButOnlyInTheValueNotInToString() {
        ConnectionCredentials creds = ConnectionCredentials.password("operator", "s3cr3t");
        assertThat(creds.hasSecret()).isTrue();
        assertThat(creds.secret()).isEqualTo("s3cr3t");
        // toString must not leak the secret into logs / summaries / error messages.
        assertThat(creds.toString()).doesNotContain("s3cr3t").contains("***").contains("operator");
    }
}
