package com.ainclusive.iotsim.app.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RepositoryDataSourceProjectResolverTest {

    /** Minimal fake: only findById is exercised. */
    private static DataSourceRepository repoReturning(DataSourceRow row) {
        return new DataSourceRepository() {
            @Override
            public DataSourceRow insert(String p, String n, String pr, String b, String e,
                    String r, String c) {
                throw new UnsupportedOperationException();
            }
            @Override
            public List<DataSourceRow> findByProject(String projectId) {
                throw new UnsupportedOperationException();
            }
            @Override
            public Optional<DataSourceRow> findById(String id) {
                return Optional.ofNullable(row);
            }
            @Override
            public Optional<DataSourceRow> update(String id, String n, String e, String r,
                    boolean en, long v) {
                throw new UnsupportedOperationException();
            }
            @Override
            public boolean deleteById(String id) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    void resolvesProjectOfAKnownDataSource() {
        DataSourceRow row = sampleRow("d1", "proj-1");
        var resolver = new RepositoryDataSourceProjectResolver(repoReturning(row));
        assertThat(resolver.projectOf("d1")).contains("proj-1");
    }

    @Test
    void emptyForUnknownDataSource() {
        var resolver = new RepositoryDataSourceProjectResolver(repoReturning(null));
        assertThat(resolver.projectOf("nope")).isEmpty();
    }

    // DataSourceRow components (verified):
    // (id, projectId, name, protocol, basis, schemaId, schemaVersion, endpoint,
    //  runtimeConfig, enabled, createdAt, updatedAt, createdBy, version).
    // Only id + projectId are asserted; the rest are placeholders.
    private static DataSourceRow sampleRow(String id, String projectId) {
        java.time.OffsetDateTime t = java.time.OffsetDateTime.parse("2026-01-01T00:00:00Z");
        return new DataSourceRow(id, projectId, "name", "opcua", "PROVIDED",
                null, null, "{}", "{}", true, t, t, "tester", 1L);
    }
}
