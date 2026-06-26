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

    @Test
    void nullModeIsRejected() {
        assertThatThrownBy(() -> new ConnectionCredentials(null, "user", "s3cr3t", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void externalRefDropsAnyStraySecretAndUsername() {
        // Defense-in-depth: a secret/username passed alongside EXTERNAL_REF must be
        // discarded by the constructor, never retained — only the (non-secret) ref survives.
        ConnectionCredentials creds = new ConnectionCredentials(
                ConnectionCredentials.Mode.EXTERNAL_REF, "operator", "s3cr3t", "vault://key");
        assertThat(creds.username()).isNull();
        assertThat(creds.secret()).isNull();
        assertThat(creds.secretRef()).isEqualTo("vault://key");
        assertThat(creds.hasSecret()).isFalse();
        assertThat(creds.toString()).doesNotContain("s3cr3t");
    }

    @Test
    void anonymousDropsAnyStraySecret() {
        // A secret passed with ANONYMOUS must not survive — the source is reached with no credentials.
        ConnectionCredentials creds = new ConnectionCredentials(
                ConnectionCredentials.Mode.ANONYMOUS, "operator", "s3cr3t", "vault://key");
        assertThat(creds.username()).isNull();
        assertThat(creds.secret()).isNull();
        assertThat(creds.secretRef()).isNull();
        assertThat(creds.hasSecret()).isFalse();
    }

    @Test
    void passwordDropsAnyStraySecretRef() {
        // PASSWORD keeps username/secret but drops a stray external reference.
        ConnectionCredentials creds = new ConnectionCredentials(
                ConnectionCredentials.Mode.PASSWORD, "operator", "s3cr3t", "vault://stray");
        assertThat(creds.username()).isEqualTo("operator");
        assertThat(creds.secret()).isEqualTo("s3cr3t");
        assertThat(creds.secretRef()).isNull();
    }

    @Test
    void externalRefCarriesNoSecretValue() {
        // The reference is not itself a secret: hasSecret() is false and there is no secret value.
        ConnectionCredentials creds = ConnectionCredentials.externalRef("vault://key");
        assertThat(creds.hasSecret()).isFalse();
        assertThat(creds.secret()).isNull();
        assertThat(creds.secretRef()).isEqualTo("vault://key");
    }
}
