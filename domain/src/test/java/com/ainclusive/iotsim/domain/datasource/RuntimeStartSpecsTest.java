package com.ainclusive.iotsim.domain.datasource;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaWithNodes;
import com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RuntimeStartSpecsTest {

    @Test
    void simulatorPortIsReadFromColumn() {
        DataSourceRow source = row(4840);
        RuntimeStartSpec spec = RuntimeStartSpecs.of(emptySchemas(), source);
        assertThat(spec.listenPort()).isEqualTo(4840);
    }

    @Test
    void zeroPortPassedThrough() {
        DataSourceRow source = row(0);
        RuntimeStartSpec spec = RuntimeStartSpecs.of(emptySchemas(), source);
        assertThat(spec.listenPort()).isEqualTo(0);
    }

    @Test
    void buildsEndpointSecurityFromRow() {
        String stored = "{\"userTokens\":{\"anonymous\":false,"
                + "\"username\":{\"enabled\":true,\"users\":[{\"username\":\"op\",\"passwordHash\":\"h\"}]}}}";
        DataSourceRow row = rowWithSecurityConfig(stored);
        RuntimeStartSpec spec = RuntimeStartSpecs.of(emptySchemas(), row);
        assertThat(spec.endpointSecurity().usernameEnabled()).isTrue();
        assertThat(spec.endpointSecurity().anonymousAllowed()).isFalse();
        assertThat(spec.endpointSecurity().users()).singleElement()
                .satisfies(u -> assertThat(u.username()).isEqualTo("op"));
    }

    private static DataSourceRow row(int simulatorPort) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new DataSourceRow("ds-1", "proj-1", "Test", "OPC_UA", "MANUAL",
                null, null, simulatorPort, null, "{}", null, false, now, now, "local", 0);
    }

    private static DataSourceRow rowWithSecurityConfig(String securityConfig) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new DataSourceRow("ds-1", "proj-1", "Test", "OPC_UA", "MANUAL",
                null, null, 4840, null, "{}", securityConfig, false, now, now, "local", 0);
    }

    private static SchemaRepository emptySchemas() {
        return new SchemaRepository() {
            @Override
            public Optional<SchemaWithNodes> findCurrent(String dataSourceId) {
                return Optional.empty();
            }

            @Override
            public SchemaWithNodes saveNewVersion(String dataSourceId, List<SchemaNode> nodes) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
