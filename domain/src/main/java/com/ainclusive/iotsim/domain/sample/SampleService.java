package com.ainclusive.iotsim.domain.sample;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.persistence.project.ProjectRepository;
import com.ainclusive.iotsim.persistence.recording.RecordingRepository;
import com.ainclusive.iotsim.persistence.sample.SampleRepository;
import com.ainclusive.iotsim.persistence.sample.SampleRow;
import java.util.List;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class SampleService {

    private final SampleRepository samples;
    private final RecordingRepository recordings;
    private final ProjectRepository projects;
    private final ObjectMapper json;

    public SampleService(SampleRepository samples, RecordingRepository recordings,
            ProjectRepository projects, ObjectMapper json) {
        this.samples = samples;
        this.recordings = recordings;
        this.projects = projects;
        this.json = json;
    }

    public Sample create(String projectId, String derivedFromRecordingId,
            String name, String selection, List<String> tags, String actor) {
        requireProject(projectId);
        if (derivedFromRecordingId != null) {
            recordings.findById(derivedFromRecordingId)
                    .filter(r -> r.projectId().equals(projectId))
                    .orElseThrow(() -> new ResourceNotFoundException("Recording", derivedFromRecordingId));
        }
        String tagsJson = json.writeValueAsString(tags != null ? tags : List.of());
        String selectionJson = selection != null ? selection : "{}";
        SampleRow row = samples.create(projectId, derivedFromRecordingId, name, selectionJson, tagsJson, actor);
        return map(row);
    }

    public List<Sample> list(String projectId) {
        requireProject(projectId);
        return samples.findByProject(projectId).stream().map(this::map).toList();
    }

    public Sample get(String projectId, String id) {
        return map(requireSample(projectId, id));
    }

    public void delete(String projectId, String id) {
        requireSample(projectId, id);
        samples.deleteById(id);
    }

    private void requireProject(String projectId) {
        if (projects.findById(projectId).isEmpty()) {
            throw new ResourceNotFoundException("Project", projectId);
        }
    }

    private SampleRow requireSample(String projectId, String id) {
        return samples.findById(id)
                .filter(r -> r.projectId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("Sample", id));
    }

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private Sample map(SampleRow r) {
        List<String> tagList = json.readValue(r.tags() != null ? r.tags() : "[]", STRING_LIST);
        return new Sample(r.id(), r.projectId(), r.derivedFromRecordingId(), r.name(),
                r.selection(), tagList, r.createdAt().toInstant(), r.createdBy(), r.version());
    }
}
