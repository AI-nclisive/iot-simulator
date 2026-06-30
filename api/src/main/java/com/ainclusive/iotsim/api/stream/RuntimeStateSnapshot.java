package com.ainclusive.iotsim.api.stream;

import com.ainclusive.iotsim.platform.runtime.RuntimeController;
import com.ainclusive.iotsim.platform.runtime.SourceError;
import com.ainclusive.iotsim.platform.runtime.SourceHealth;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Builds the current per-source {@code runtime-state} snapshot sent on connect to the
 * runtime stream (IS-051): for each data source in the project, its
 * {@link RuntimeController#health} — the runtime state
 * (RUNNING/STOPPED/STARTING/ERROR/STALE) plus the most recent error (IS-053),
 * {@code null} when none.
 */
@Component
public class RuntimeStateSnapshot {

    /** One source's current runtime state plus its most recent error (null when none). */
    public record SourceRuntimeState(String dataSourceId, String state, SourceError lastError) {}

    private final RuntimeController runtimeController;
    private final ProjectSources sources;

    public RuntimeStateSnapshot(RuntimeController runtimeController, ProjectSources sources) {
        this.runtimeController = runtimeController;
        this.sources = sources;
    }

    List<LiveEvent> initialFor(String projectId) {
        List<SourceRuntimeState> states = sources.idsOf(projectId).stream()
                .map(id -> {
                    SourceHealth h = runtimeController.health(id);
                    return new SourceRuntimeState(id, h.state(), h.lastError());
                })
                .toList();
        return List.of(new LiveEvent(LiveEvent.NO_SEQ, "runtime-state", states, Instant.now()));
    }
}
