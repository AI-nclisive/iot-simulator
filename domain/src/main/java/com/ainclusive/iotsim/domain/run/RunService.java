package com.ainclusive.iotsim.domain.run;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.replay.ReplayLiveRunService;
import com.ainclusive.iotsim.domain.replay.ReplayService;
import com.ainclusive.iotsim.domain.scenario.ScenarioLiveRunService;
import com.ainclusive.iotsim.domain.support.Page;
import com.ainclusive.iotsim.domain.support.PageCursor;
import com.ainclusive.iotsim.domain.synthetic.SyntheticLiveRunService;
import com.ainclusive.iotsim.domain.synthetic.SyntheticRunService;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.evidence.EvidenceRepository;
import com.ainclusive.iotsim.persistence.run.RunRepository;
import com.ainclusive.iotsim.persistence.run.RunRow;
import com.ainclusive.iotsim.persistence.runtimeevent.RuntimeEventRepository;
import com.ainclusive.iotsim.persistence.scenario.ScenarioRepository;
import com.ainclusive.iotsim.platform.runtime.RuntimeController;
import com.ainclusive.iotsim.platform.runtime.SourceHealth;
import com.ainclusive.iotsim.protocolmodel.DeterministicSettings;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Unified runs resource + test-control (IS-089). Read/list/state/stop, and a single
 * automation-facing start that routes to the replay/synthetic/scenario services.
 */
@Service
public class RunService {

    private static final Set<String> TERMINAL = Set.of("STOPPED", "FAILED", "COMPLETED");

    private final RunRepository runs;
    private final DataSourceRepository dataSources;
    private final ScenarioRepository scenarios;
    private final RuntimeController runtime;
    private final RuntimeEventRepository events;
    private final EvidenceRepository evidence;
    private final ReplayService replay;                   // batch primitive (scenario REPLAY steps)
    private final ReplayLiveRunService replayLive;        // live standalone replay (IS-140)
    private final SyntheticRunService synthetic;          // batch primitive (scenarios)
    private final SyntheticLiveRunService syntheticLive;  // live standalone feed (IS-119)
    private final ScenarioLiveRunService scenarioLive;    // async scenario engine (IS-141)

    public RunService(RunRepository runs, DataSourceRepository dataSources, ScenarioRepository scenarios,
            RuntimeController runtime, RuntimeEventRepository events, EvidenceRepository evidence,
            ReplayService replay, ReplayLiveRunService replayLive, SyntheticRunService synthetic,
            SyntheticLiveRunService syntheticLive, ScenarioLiveRunService scenarioLive) {
        this.runs = runs;
        this.dataSources = dataSources;
        this.scenarios = scenarios;
        this.runtime = runtime;
        this.events = events;
        this.evidence = evidence;
        this.replay = replay;
        this.replayLive = replayLive;
        this.synthetic = synthetic;
        this.syntheticLive = syntheticLive;
        this.scenarioLive = scenarioLive;
    }

    public Page<RunView> listPaged(String projectId, String cursor, Integer limit) {
        int size = PageCursor.clamp(limit);
        PageCursor.Parts after = PageCursor.decode(cursor);
        OffsetDateTime afterAt = after != null ? after.at() : null;
        String afterId = after != null ? after.id() : null;
        List<RunRow> rows = runs.findByProjectPaged(projectId, afterAt, afterId, size + 1);
        String next = null;
        if (rows.size() > size) {
            rows = rows.subList(0, size);
            RunRow lastRow = rows.get(rows.size() - 1);
            next = PageCursor.encode(lastRow.createdAt(), lastRow.id());
        }
        Map<String, String> sourceNames = sourceNames(projectId);
        return new Page<>(rows.stream().map(r -> view(r, sourceNames)).toList(), next, size);
    }

    public RunView get(String projectId, String id) {
        return view(require(projectId, id), sourceNames(projectId));
    }

    public RunState stateOf(String projectId, String id) {
        RunRow run = require(projectId, id);
        List<SourceState> sources = run.sourceIds().stream().map(sid -> {
            SourceHealth h = runtime.health(sid);
            String lastError = h != null && h.lastError() != null ? h.lastError().reason() : null;
            return new SourceState(sid, h != null ? h.state() : "STOPPED", lastError);
        }).toList();
        return new RunState(run.state(), sources);
    }

    public RunView stop(String projectId, String id) {
        RunRow run = require(projectId, id);
        boolean wasLiveReplay = replayLive.stopIfLive(id);     // no-op unless it is a live replay run
        boolean wasLiveSynthetic = syntheticLive.stopIfLive(id); // no-op unless it is a live synthetic run
        scenarioLive.stopIfLive(id);                  // no-op unless it is an async scenario run; owns its own STOPPED event
        run.sourceIds().forEach(runtime::stop);
        boolean wasNonTerminal = !TERMINAL.contains(run.state());
        RunRow after = wasNonTerminal ? runs.end(id, "STOPPED", OffsetDateTime.now(ZoneOffset.UTC)) : run;
        // IS-182: only stamp here for the two live services that don't own their own STOPPED
        // write (scenario's background thread already appends its own terminal event). Reuse
        // after.endedAt() rather than a fresh now() so runs.endedAt and runtime_events.at match.
        if (wasNonTerminal && (wasLiveReplay || wasLiveSynthetic)) {
            String dataSourceId = run.sourceIds().isEmpty() ? null : run.sourceIds().get(0);
            RunCompletionEvents.appendTerminal(events, projectId, dataSourceId, id, "STOPPED", after.endedAt());
        }
        // IS-187: flip evidence out of CAPTURING for every manual stop this method itself
        // ends (a scenario's own async loop stamps its own evidence status via safeEnd).
        if (wasNonTerminal) {
            EvidenceCompletionStamp.finalizeStatus(evidence, id, "STOPPED");
        }
        return view(after, sourceNames(projectId));
    }

    public RunView start(String projectId, StartRunCommand cmd) {
        String initiator = cmd.initiator();
        if (initiator == null || initiator.isBlank()) {
            throw new IllegalArgumentException("initiator is required for an automation run");
        }
        String runId = switch (cmd.kind() == null ? "" : cmd.kind()) {
            case "REPLAY" -> {
                requireField(cmd.dataSourceId(), "dataSourceId");
                requireField(cmd.recordingId(), "recordingId");
                DeterministicSettings settings = cmd.seed() != null
                        ? new DeterministicSettings(cmd.seed(),
                                cmd.startTime() != null ? Instant.parse(cmd.startTime()) : Instant.now())
                        : null;
                yield replayLive.start(projectId, cmd.dataSourceId(), cmd.recordingId(), settings,
                        Boolean.TRUE.equals(cmd.compatibilityAck()), "AUTOMATION", initiator).runId();
            }
            case "SYNTHETIC" -> {
                requireField(cmd.dataSourceId(), "dataSourceId");
                if (cmd.durationMs() != null && cmd.durationMs() <= 0) {
                    throw new IllegalArgumentException("durationMs (cap) must be > 0 when set");
                }
                yield syntheticLive.start(projectId, cmd.dataSourceId(), cmd.durationMs(),
                        "AUTOMATION", initiator).runId();
            }
            case "SCENARIO" -> {
                requireField(cmd.scenarioId(), "scenarioId");
                yield scenarioLive.start(projectId, cmd.scenarioId(), "AUTOMATION", initiator).runId();
            }
            default -> throw new IllegalArgumentException("unknown run kind: " + cmd.kind());
        };
        return view(require(projectId, runId), sourceNames(projectId));
    }

    // ---- helpers ----

    private RunRow require(String projectId, String id) {
        return runs.findById(id)
                .filter(r -> r.projectId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("Run", id));
    }

    private static void requireField(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private Map<String, String> sourceNames(String projectId) {
        return dataSources.findByProject(projectId).stream()
                .collect(Collectors.toMap(DataSourceRow::id, DataSourceRow::name));
    }

    private RunView view(RunRow r, Map<String, String> sourceNames) {
        String relatedLabel = null;
        if ("SCENARIO".equals(r.kind()) && r.scenarioId() != null) {
            relatedLabel = scenarios.findById(r.scenarioId()).map(s -> s.name()).orElse(null);
        } else if (!r.sourceIds().isEmpty()) {
            relatedLabel = sourceNames.get(r.sourceIds().get(0));
        }
        String label = relatedLabel != null ? relatedLabel + " " + r.kind().toLowerCase() : r.kind();
        return new RunView(r.id(), r.projectId(), r.kind(), r.trigger(), r.initiator(), r.state(),
                r.scenarioId(), r.evidenceId(), r.parentRunId(), r.sourceIds(),
                instant(r.startedAt()), instant(r.endedAt()), instant(r.createdAt()), label, relatedLabel);
    }

    private static Instant instant(OffsetDateTime t) {
        return t != null ? t.toInstant() : null;
    }
}
