package com.ainclusive.iotsim.api.stream;

import java.util.List;

/** Lists the data-source ids of a project — narrow seam for the runtime-state snapshot. */
@FunctionalInterface
public interface ProjectSources {
    List<String> idsOf(String projectId);
}
