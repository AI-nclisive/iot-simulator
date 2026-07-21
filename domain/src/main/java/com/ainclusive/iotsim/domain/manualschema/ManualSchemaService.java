package com.ainclusive.iotsim.domain.manualschema;

import com.ainclusive.iotsim.domain.common.ConcurrencyConflictException;
import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.datasource.Protocol;
import com.ainclusive.iotsim.domain.support.Page;
import com.ainclusive.iotsim.domain.support.PageCursor;
import com.ainclusive.iotsim.persistence.manualschema.ManualSchemaRepository;
import com.ainclusive.iotsim.persistence.manualschema.ManualSchemaRow;
import com.ainclusive.iotsim.persistence.project.ProjectRepository;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * CRUD + duplicate over standalone {@link ManualSchema} artifacts (IS-172).
 * See backend-specs/03_DOMAIN_MODEL.md §ManualSchema and 05_API_CONTRACT.md §Manual schemas.
 *
 * <p>Consumed only via synthetic source creation's {@code manualSchemaId} (IS-173), which
 * copies a schema's nodes by snapshot — later edits here never affect a source already created
 * from it.
 */
@Service
public class ManualSchemaService {

    private static final TypeReference<List<SchemaNode>> SCHEMA_NODE_LIST =
            new TypeReference<>() {};

    private final ManualSchemaRepository manualSchemas;
    private final ProjectRepository projects;
    private final ObjectMapper json;

    public ManualSchemaService(ManualSchemaRepository manualSchemas, ProjectRepository projects, ObjectMapper json) {
        this.manualSchemas = manualSchemas;
        this.projects = projects;
        this.json = json;
    }

    public Page<ManualSchema> listPaged(String projectId, String cursor, Integer limit) {
        requireProject(projectId);
        int size = PageCursor.clamp(limit);
        PageCursor.Parts after = PageCursor.decode(cursor);
        OffsetDateTime afterAt = after != null ? after.at() : null;
        String afterId = after != null ? after.id() : null;
        List<ManualSchemaRow> rows = manualSchemas.findByProjectPaged(projectId, afterAt, afterId, size + 1);
        String nextCursor = null;
        if (rows.size() > size) {
            rows = rows.subList(0, size);
            ManualSchemaRow last = rows.get(rows.size() - 1);
            nextCursor = PageCursor.encode(last.createdAt(), last.id());
        }
        return new Page<>(rows.stream().map(this::map).toList(), nextCursor, size);
    }

    public ManualSchema get(String projectId, String id) {
        return map(requireRow(projectId, id));
    }

    public ManualSchema create(String projectId, String protocol, String name, String description,
            List<SchemaNode> nodes, String createdBy) {
        requireProject(projectId);
        validateProtocol(protocol);
        validateNodes(nodes);
        return map(manualSchemas.create(projectId, protocol, name, description, json.writeValueAsString(nodes),
                createdBy));
    }

    public ManualSchema update(String projectId, String id, String name, String description,
            List<SchemaNode> nodes, long expectedVersion) {
        requireRow(projectId, id);
        validateNodes(nodes);
        return manualSchemas.update(id, name, description, json.writeValueAsString(nodes), expectedVersion)
                .map(this::map)
                .orElseThrow(() -> new ConcurrencyConflictException("ManualSchema", id, expectedVersion));
    }

    /** Save-as-new: copies an existing manual schema's protocol/description/nodes under a new name. */
    public ManualSchema duplicate(String projectId, String id, String newName, String createdBy) {
        requireRow(projectId, id);
        return manualSchemas.duplicate(id, newName, createdBy)
                .map(this::map)
                .orElseThrow(() -> new ResourceNotFoundException("ManualSchema", id));
    }

    public void delete(String projectId, String id) {
        requireRow(projectId, id);
        manualSchemas.deleteById(id);
    }

    private ManualSchemaRow requireRow(String projectId, String id) {
        return manualSchemas.findById(id)
                .filter(r -> r.projectId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("ManualSchema", id));
    }

    private void requireProject(String projectId) {
        if (projects.findById(projectId).isEmpty()) {
            throw new ResourceNotFoundException("Project", projectId);
        }
    }

    private static void validateProtocol(String protocol) {
        if (protocol == null) {
            throw new IllegalArgumentException("protocol is required");
        }
        Protocol.valueOf(protocol); // invalid -> IllegalArgumentException -> 400
    }

    /** Mirrors {@code SchemaService.validate}: rejects duplicate nodeIds or paths. */
    private static void validateNodes(List<SchemaNode> nodes) {
        if (nodes == null) {
            throw new IllegalArgumentException("nodes are required");
        }
        Set<String> paths = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (SchemaNode n : nodes) {
            if (!ids.add(n.nodeId())) {
                throw new IllegalArgumentException("duplicate nodeId: " + n.nodeId());
            }
            if (!paths.add(n.path())) {
                throw new IllegalArgumentException("duplicate node path: " + n.path());
            }
        }
    }

    private ManualSchema map(ManualSchemaRow r) {
        List<SchemaNode> nodes = r.nodesJson() == null || r.nodesJson().isBlank()
                ? List.of()
                : json.readValue(r.nodesJson(), SCHEMA_NODE_LIST);
        return new ManualSchema(r.id(), r.projectId(), r.protocol(), r.name(), r.description(), nodes,
                r.createdAt().toInstant(), r.updatedAt().toInstant(), r.createdBy(), r.version());
    }
}
