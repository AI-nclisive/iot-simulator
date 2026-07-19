package com.ainclusive.iotsim.domain.project;

import com.ainclusive.iotsim.domain.common.ConcurrencyConflictException;
import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.project.Project.ProjectStatus;
import com.ainclusive.iotsim.domain.support.Page;
import com.ainclusive.iotsim.domain.support.PageCursor;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.project.ProjectRepository;
import com.ainclusive.iotsim.persistence.project.ProjectRow;
import com.ainclusive.iotsim.persistence.recording.RecordingRepository;
import com.ainclusive.iotsim.persistence.recording.RecordingRow;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaWithNodes;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Project lifecycle (backend-specs/03_DOMAIN_MODEL.md). */
@Service
public class ProjectService {

    private final ProjectRepository repository;
    private final DataSourceRepository dataSources;
    private final SchemaRepository schemas;
    private final RecordingRepository recordings;

    public ProjectService(ProjectRepository repository, DataSourceRepository dataSources,
            SchemaRepository schemas, RecordingRepository recordings) {
        this.repository = repository;
        this.dataSources = dataSources;
        this.schemas = schemas;
        this.recordings = recordings;
    }

    public Project create(String name, String description, String actor) {
        return toDomain(repository.insert(name, description, actor));
    }

    public List<Project> list() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    public Page<Project> listPaged(String status, String cursor, Integer limit) {
        int size = PageCursor.clamp(limit);
        PageCursor.Parts after = PageCursor.decode(cursor);
        OffsetDateTime afterAt = after != null ? after.at() : null;
        String afterId = after != null ? after.id() : null;
        List<ProjectRow> rows = repository.findAllPaged(status, afterAt, afterId, size + 1);
        String nextCursor = null;
        if (rows.size() > size) {
            rows = rows.subList(0, size);
            ProjectRow last = rows.get(rows.size() - 1);
            nextCursor = PageCursor.encode(last.createdAt(), last.id());
        }
        return new Page<>(rows.stream().map(this::toDomain).toList(), nextCursor, size);
    }

    public Project get(String id) {
        return repository.findById(id).map(this::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));
    }

    public Project update(String id, String name, String description, long expectedVersion) {
        return repository.update(id, name, description, expectedVersion)
                .map(this::toDomain)
                .orElseGet(() -> {
                    // No row matched (id + version). Distinguish 404 from 409.
                    if (repository.findById(id).isEmpty()) {
                        throw new ResourceNotFoundException("Project", id);
                    }
                    throw new ConcurrencyConflictException("Project", id, expectedVersion);
                });
    }

    public Project archive(String id) {
        return repository.archive(id).map(this::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));
    }

    public void delete(String id) {
        if (!repository.deleteById(id)) {
            throw new ResourceNotFoundException("Project", id);
        }
    }

    /**
     * Deep-copies a project: project metadata, all data sources (config + schema),
     * and recording metadata rows (no timeline data). The copy gets a new ID,
     * name = "&lt;original name&gt; (copy)", createdAt = now, data sources are disabled
     * with runtimeState = STOPPED. Returns 404 if the source project does not exist.
     */
    @Transactional
    public Project duplicate(String sourceId) {
        ProjectRow source = repository.findById(sourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", sourceId));

        // Create the new project.
        ProjectRow copy = repository.insert(
                source.name() + " (copy)", source.description(), source.createdBy());

        // Copy each data source, tracking old->new ID mapping for recording re-pointing.
        Map<String, String> dsIdMap = new HashMap<>();
        Map<String, Integer> dsSchemaVersionMap = new HashMap<>();
        for (DataSourceRow ds : dataSources.findByProject(sourceId)) {
            DataSourceRow newDs = dataSources.insert(
                    copy.id(), ds.name(), ds.protocol(), ds.basis(),
                    ds.simulatorPort(), ds.realDeviceEndpoint(), ds.runtimeConfig(),
                    ds.securityConfig(), ds.createdBy());
            dsIdMap.put(ds.id(), newDs.id());

            // Copy schema if present and non-empty (mirrors DataSourceService.duplicate() guard).
            Optional<SchemaWithNodes> schema = schemas.findCurrent(ds.id());
            if (schema.isPresent() && !schema.get().nodes().isEmpty()) {
                SchemaWithNodes saved = schemas.saveNewVersion(newDs.id(), schema.get().nodes());
                dsSchemaVersionMap.put(ds.id(), saved.version());
            }
        }

        // Copy recording metadata rows, re-pointing to the new data source IDs.
        for (RecordingRow rec : recordings.findByProject(sourceId)) {
            // dataSourceId is optional (IS-160: recordings are protocol-scoped, not
            // source-instance-scoped) — a recording with no captured-from source (e.g.
            // imported without a matching dataSourceId) duplicates with newDsId = null.
            String newDsId = rec.dataSourceId() != null ? dsIdMap.get(rec.dataSourceId()) : null;
            if (rec.dataSourceId() != null && newDsId == null) {
                throw new IllegalStateException(
                        "Recording " + rec.id() + " references unknown data source " + rec.dataSourceId());
            }
            int newSchemaVersion = dsSchemaVersionMap.getOrDefault(rec.dataSourceId(), rec.schemaVersion());
            recordings.create(copy.id(), newDsId, rec.protocol(), newSchemaVersion, rec.origin(),
                    "SCHEMA_AND_DATA", rec.name(), rec.createdBy(), rec.schemaNodesJson());
        }

        return toDomain(copy);
    }

    private Project toDomain(ProjectRow r) {
        return new Project(
                r.id(),
                r.name(),
                r.description(),
                ProjectStatus.valueOf(r.status()),
                r.createdAt().toInstant(),
                r.updatedAt().toInstant(),
                r.createdBy(),
                r.version());
    }
}
