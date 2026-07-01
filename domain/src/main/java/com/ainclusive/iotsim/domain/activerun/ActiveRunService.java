package com.ainclusive.iotsim.domain.activerun;

import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.run.RunRepository;
import com.ainclusive.iotsim.persistence.run.RunRow;
import com.ainclusive.iotsim.persistence.scenario.ScenarioRepository;
import com.ainclusive.iotsim.persistence.scenario.ScenarioRow;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Aggregates currently-active runs (state RUNNING or QUEUED) for a project into
 * the dashboard overview shape (IS-122).
 *
 * <p>For each run the related label is resolved as:
 * <ul>
 *   <li>SCENARIO runs → the scenario name (looked up by scenarioId).
 *   <li>RECORDING / REPLAY runs → the data-source name of the first participating source.
 * </ul>
 * The related label (and relatedSourceId) are nullable; no lookup failure aborts the list.
 */
@Service
public class ActiveRunService {

    private final RunRepository runs;
    private final DataSourceRepository dataSources;
    private final ScenarioRepository scenarios;

    public ActiveRunService(RunRepository runs, DataSourceRepository dataSources,
            ScenarioRepository scenarios) {
        this.runs = runs;
        this.dataSources = dataSources;
        this.scenarios = scenarios;
    }

    /**
     * Returns all active (RUNNING or QUEUED) runs for {@code projectId}, newest first.
     * Each result is enriched with a human-readable label and related-entity info.
     */
    public List<ActiveRun> getActiveRuns(String projectId) {
        List<RunRow> activeRows = runs.findActiveByProject(projectId);
        if (activeRows.isEmpty()) {
            return List.of();
        }

        // Collect all source IDs referenced by any run (first source per run suffices for label).
        List<String> allSourceIds = activeRows.stream()
                .flatMap(r -> r.sourceIds().stream())
                .distinct()
                .toList();

        // Batch-load data sources (avoid per-run queries).
        Map<String, String> sourceNameById = dataSources.findByProject(projectId).stream()
                .filter(ds -> allSourceIds.contains(ds.id()))
                .collect(Collectors.toMap(DataSourceRow::id, DataSourceRow::name));

        // Batch-load scenarios for SCENARIO runs.
        Map<String, String> scenarioNameById = activeRows.stream()
                .filter(r -> "SCENARIO".equals(r.kind()) && r.scenarioId() != null)
                .map(RunRow::scenarioId)
                .distinct()
                .flatMap(sid -> scenarios.findById(sid).stream())
                .collect(Collectors.toMap(ScenarioRow::id, ScenarioRow::name));

        return activeRows.stream().map(r -> toActiveRun(r, sourceNameById, scenarioNameById)).toList();
    }

    private static ActiveRun toActiveRun(RunRow r, Map<String, String> sourceNameById,
            Map<String, String> scenarioNameById) {
        String processType = mapProcessType(r.kind());
        String runState = mapRunState(r.state());
        Instant startedAt = r.startedAt() != null ? r.startedAt().toInstant() : null;
        String initiator = r.initiator() != null ? r.initiator() : "local";

        // Label: for SCENARIO use scenario name, otherwise use the first source name or run id.
        String relatedSourceId = null;
        String relatedLabel = null;

        if ("SCENARIO".equals(r.kind()) && r.scenarioId() != null) {
            relatedLabel = scenarioNameById.get(r.scenarioId());
        } else if (!r.sourceIds().isEmpty()) {
            relatedSourceId = r.sourceIds().get(0);
            relatedLabel = sourceNameById.get(relatedSourceId);
        }

        // The run's own label: for SCENARIO use scenario name; for others use data-source name + kind.
        String label = buildLabel(r.kind(), processType, relatedLabel, relatedSourceId, r.id());

        return new ActiveRun(r.id(), label, processType, runState, startedAt, initiator,
                relatedSourceId, relatedLabel);
    }

    private static String buildLabel(String kind, String processType, String relatedLabel,
            String relatedSourceId, String runId) {
        if ("SCENARIO".equals(kind)) {
            return relatedLabel != null ? relatedLabel : processType;
        }
        if (relatedLabel != null) {
            return relatedLabel + " " + processType.toLowerCase();
        }
        return processType + " " + abbreviate(runId);
    }

    private static String mapProcessType(String kind) {
        return switch (kind == null ? "" : kind) {
            case "RECORDING" -> "Recording";
            case "REPLAY" -> "Replay";
            case "SCENARIO" -> "Scenario";
            default -> kind != null ? kind : "Unknown";
        };
    }

    private static String mapRunState(String state) {
        return switch (state == null ? "" : state) {
            case "RUNNING" -> "running";
            case "QUEUED" -> "queued";
            case "FAILED" -> "failed";
            case "COMPLETED" -> "completed";
            case "STOPPED" -> "stopped";
            default -> state != null ? state.toLowerCase() : "unknown";
        };
    }

    /** Short suffix of the ULID so labels remain human-readable even without a source name. */
    private static String abbreviate(String id) {
        if (id == null || id.length() < 6) {
            return id;
        }
        return "…" + id.substring(id.length() - 6);
    }
}
