package com.ainclusive.iotsim.api.datasource;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.domain.datasource.CredentialState;
import com.ainclusive.iotsim.domain.datasource.DataSource;
import com.ainclusive.iotsim.domain.datasource.Protocol;
import com.ainclusive.iotsim.domain.datasource.RuntimeState;
import com.ainclusive.iotsim.domain.datasource.SourceBasis;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link DataSourceController.DataSourceResponse#from(DataSource)}.
 * Verifies that the response mapping redacts stored security config (drops password hashes).
 */
class DataSourceResponseTest {

    @Test
    void fromRedactsPasswordHashFromSecurityConfig() {
        String storedSecurityConfig =
                "{\"userTokens\":{\"anonymous\":false,\"username\":{\"enabled\":true,"
                + "\"users\":[{\"username\":\"op\",\"passwordHash\":\"bcrypt$2a$somehash\"}]}}}";

        DataSource ds = new DataSource(
                "ds-1", "proj-1", "My Source",
                Protocol.OPC_UA, SourceBasis.MANUAL,
                null, null,
                4840, null, null,
                storedSecurityConfig,
                false,
                RuntimeState.STOPPED, CredentialState.MISSING,
                "opc.tcp://localhost:4840/iotsim",
                Instant.EPOCH, Instant.EPOCH, "test-user", 0L);

        DataSourceController.DataSourceResponse response = DataSourceController.DataSourceResponse.from(ds, 0);

        assertThat(response.securityConfig())
                .contains("op")
                .doesNotContain("passwordHash")
                .doesNotContain("bcrypt$2a$somehash");
    }
}
