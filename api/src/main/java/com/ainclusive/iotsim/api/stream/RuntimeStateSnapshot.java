package com.ainclusive.iotsim.api.stream;

import com.ainclusive.iotsim.platform.runtime.RuntimeController;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Builds the current per-source {@code runtime-state} snapshot sent on connect to the
 * runtime stream (IS-051): for each data source in the project, its
 * {@link RuntimeController#state} (RUNNING/STOPPED/STARTING/ERROR/STALE).
 */
@Component
public class RuntimeStateSnapshot {

    /** One source's current runtime state. */
    public record SourceRuntimeState(String dataSourceId, String state) {}

    private final RuntimeController runtimeController;
    private final ProjectSources sources;

    public RuntimeStateSnapshot(RuntimeController runtimeController, ProjectSources sources) {
        this.runtimeController = runtimeController;
        this.sources = sources;
    }

    List<LiveEvent> initialFor(String projectId) {
        List<SourceRuntimeState> states = sources.idsOf(projectId).stream()
                .map(id -> new SourceRuntimeState(id, runtimeController.state(id)))
                .toList();
        return List.of(new LiveEvent(LiveEvent.NO_SEQ, "runtime-state", states, Instant.now()));
    }
}
