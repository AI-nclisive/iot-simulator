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
