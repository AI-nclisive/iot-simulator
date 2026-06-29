package com.ainclusive.iotsim.api.stream;

import com.ainclusive.iotsim.domain.datasource.DataSource;
import com.ainclusive.iotsim.domain.datasource.DataSourceService;
import java.util.List;
import org.springframework.stereotype.Component;

/** {@link ProjectSources} over the domain {@code DataSourceService}. */
@Component
public final class DomainProjectSources implements ProjectSources {

    private final DataSourceService dataSources;

    public DomainProjectSources(DataSourceService dataSources) {
        this.dataSources = dataSources;
    }

    @Override
    public List<String> idsOf(String projectId) {
        return dataSources.list(projectId).stream().map(DataSource::id).toList();
    }
}
