package com.ainclusive.iotsim.domain.scenario;

import com.ainclusive.iotsim.domain.common.JsonField;
import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.recording.RecordingRepository;
import com.ainclusive.iotsim.persistence.scenario.ScenarioRepository;
import com.ainclusive.iotsim.persistence.scenario.ScenarioRow;
import com.ainclusive.iotsim.persistence.scenario.ScenarioStepRow;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaWithNodes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Computes and persists a scenario's READY/INVALID status from cross-entity checks (IS-086). */
@Service
public class ScenarioValidationService {

    private final ScenarioRepository scenarios;
    private final DataSourceRepository dataSources;
    private final RecordingRepository recordings;
    private final SchemaRepository schemas;
    private final ObjectMapper json;

    public ScenarioValidationService(ScenarioRepository scenarios, DataSourceRepository dataSources,
            RecordingRepository recordings, SchemaRepository schemas, ObjectMapper json) {
        this.scenarios = scenarios;
        this.dataSources = dataSources;
        this.recordings = recordings;
        this.schemas = schemas;
        this.json = json;
    }

    public ScenarioValidation validate(String projectId, String id) {
        ScenarioRow scenario = scenarios.findById(id)
                .filter(s -> s.projectId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("Scenario", id));

        List<ValidationIssue> issues = new ArrayList<>();
        if (scenario.steps().isEmpty()) {
            issues.add(new ValidationIssue(-1, ValidationIssue.ERROR, "scenario has no steps to run"));
        }
        for (ScenarioStepRow step : scenario.steps()) {
            validateStep(projectId, step, issues);
        }

        boolean hasError = issues.stream().anyMatch(i -> ValidationIssue.ERROR.equals(i.severity()));
        String status = hasError ? ScenarioValidation.INVALID : ScenarioValidation.READY;
        scenarios.updateStatus(id, status);
        return new ScenarioValidation(status, List.copyOf(issues));
    }

    private void validateStep(String projectId, ScenarioStepRow step, List<ValidationIssue> issues) {
        int at = step.ordinal();
        switch (step.type()) {
            case "START", "STOP" -> requireSource(projectId, step.targetSourceId(), at, issues);
            case "REPLAY" -> validateReplay(projectId, step, issues);
            case "SYNTHETIC" -> validateSynthetic(projectId, step, issues);
            case "WAIT" -> requirePositiveLong(step.params(), "durationMs", at, issues);
            case "MARKER" -> { /* always valid */ }
            case "FAULT" -> validateFault(step, at, issues);
            default -> issues.add(new ValidationIssue(at, ValidationIssue.ERROR,
                    "unknown step type: " + step.type()));
        }
    }

    private Optional<DataSourceRow> requireSource(String projectId, String sourceId, int at,
            List<ValidationIssue> issues) {
        Optional<DataSourceRow> src = sourceId == null ? Optional.empty()
                : dataSources.findById(sourceId).filter(s -> s.projectId().equals(projectId));
        if (src.isEmpty()) {
            issues.add(new ValidationIssue(at, ValidationIssue.ERROR,
                    "targetSourceId does not exist in project: " + sourceId));
        }
        return src;
    }

    private void validateReplay(String projectId, ScenarioStepRow step, List<ValidationIssue> issues) {
        int at = step.ordinal();
        if (requireSource(projectId, step.targetSourceId(), at, issues).isEmpty()) {
            return;
        }
        String recordingId = text(step.params(), "recordingId");
        if (recordingId == null || recordingId.isBlank()) {
            issues.add(new ValidationIssue(at, ValidationIssue.ERROR, "REPLAY step requires params.recordingId"));
            return;
        }
        var recording = recordings.findById(recordingId).filter(r -> r.projectId().equals(projectId));
        if (recording.isEmpty()) {
            issues.add(new ValidationIssue(at, ValidationIssue.ERROR,
                    "recording does not exist in project: " + recordingId));
            return;
        }
        int currentVersion = schemas.findCurrent(step.targetSourceId())
                .map(SchemaWithNodes::version).orElse(0);
        if (recording.get().schemaVersion() != currentVersion) {
            issues.add(new ValidationIssue(at, ValidationIssue.WARNING,
                    "recording schemaVersion " + recording.get().schemaVersion()
                            + " differs from source schema " + currentVersion
                            + " (needs compatibilityAck at run)"));
        }
    }

    private void validateSynthetic(String projectId, ScenarioStepRow step, List<ValidationIssue> issues) {
        int at = step.ordinal();
        Optional<DataSourceRow> src = requireSource(projectId, step.targetSourceId(), at, issues);
        src.ifPresent(s -> {
            if (!"SYNTHETIC".equals(s.basis())) {
                issues.add(new ValidationIssue(at, ValidationIssue.ERROR,
                        "SYNTHETIC step target is not a synthetic source: " + s.id()));
            }
        });
        requirePositiveLong(step.params(), "durationMs", at, issues);
    }

    private static final List<String> VALID_FAULT_KINDS = List.of(
            "BAD_VALUE", "MISSING_VALUE", "DELAY", "CONNECTION_DROP",
            "TIMEOUT", "PROTOCOL_ERROR", "SOURCE_UNAVAILABLE");

    private void validateFault(ScenarioStepRow step, int at, List<ValidationIssue> issues) {
        if (step.targetSourceId() == null || step.targetSourceId().isBlank()) {
            issues.add(new ValidationIssue(at, ValidationIssue.ERROR, "FAULT step requires a target source"));
            return;
        }
        String kind = text(step.params(), "kind");
        if (kind == null || kind.isBlank()) {
            issues.add(new ValidationIssue(at, ValidationIssue.ERROR, "FAULT step requires a kind"));
            return;
        }
        if (!VALID_FAULT_KINDS.contains(kind)) {
            issues.add(new ValidationIssue(at, ValidationIssue.WARNING, "unknown fault kind: " + kind));
        }
    }

    private void requirePositiveLong(String paramsJson, String field, int at, List<ValidationIssue> issues) {
        Long v = JsonField.longValue(json, paramsJson, field);
        if (v == null || v <= 0) {
            issues.add(new ValidationIssue(at, ValidationIssue.ERROR,
                    "step requires params." + field + " > 0"));
        }
    }

    private String text(String paramsJson, String field) {
        return JsonField.text(json, paramsJson, field);
    }
}
