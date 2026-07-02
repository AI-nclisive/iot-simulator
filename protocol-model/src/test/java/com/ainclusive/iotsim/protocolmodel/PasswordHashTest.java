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

    @Test
    void rejectsInvalidBase64Segments() {
        assertThat(PasswordHash.matches("pw", "pbkdf2-sha256$210000$not-b64!!$also!!!")).isFalse();
    }
}
