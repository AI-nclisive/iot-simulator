package com.ainclusive.iotsim.app.runtime;

import com.ainclusive.iotsim.api.stream.DataSourceProjectResolver;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Resolves a data source's project via the repository, for SSE runtime routing (IS-046). */
@Component
public final class RepositoryDataSourceProjectResolver implements DataSourceProjectResolver {

    private final DataSourceRepository dataSources;

    public RepositoryDataSourceProjectResolver(DataSourceRepository dataSources) {
        this.dataSources = dataSources;
    }

    @Override
    public Optional<String> projectOf(String dataSourceId) {
        return dataSources.findById(dataSourceId).map(DataSourceRow::projectId);
    }
}
