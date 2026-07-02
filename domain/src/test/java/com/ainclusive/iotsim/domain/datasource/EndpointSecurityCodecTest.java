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

    @Test
    void rejectsWhitespacePassword() {
        assertThatThrownBy(() -> EndpointSecurityCodec.normalizeForStorage(
                "{\"userTokens\":{\"anonymous\":true,\"username\":{\"enabled\":true,"
                + "\"users\":[{\"username\":\"op\",\"password\":\"   \"}]}}}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMalformedJson() {
        assertThatThrownBy(() -> EndpointSecurityCodec.normalizeForStorage("not-json"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
