package com.ainclusive.iotsim.api.stream;

import java.util.Optional;

/**
 * Resolves the owning project of a data source so runtime events (which carry only
 * a {@code dataSourceId}) can be routed to the per-project runtime stream. Impl is
 * wired in the {@code app} module over the data-source repository.
 */
@FunctionalInterface
public interface DataSourceProjectResolver {
    Optional<String> projectOf(String dataSourceId);
}
