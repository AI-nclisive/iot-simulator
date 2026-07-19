package com.ainclusive.iotsim.domain.schema;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.common.SchemaImpactException;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaWithNodes;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Reads and saves the protocol-neutral schema of a data-source (backend-specs/01 & 05). */
@Service
public class SchemaService {

    private final SchemaRepository schemas;
    private final DataSourceRepository dataSources;
    private final ObjectMapper json;

    public SchemaService(SchemaRepository schemas, DataSourceRepository dataSources, ObjectMapper json) {
        this.schemas = schemas;
        this.dataSources = dataSources;
        this.json = json;
    }

    public Schema get(String projectId, String dataSourceId) {
        requireSource(projectId, dataSourceId);
        return schemas.findCurrent(dataSourceId).map(this::map)
                .orElseThrow(() -> new ResourceNotFoundException("Schema", dataSourceId));
    }

    /**
     * Returns VARIABLE node counts for the given set of data-source ids (IS-149).
     * No-schema sources map to 0. Single bulk query — safe to call on the list endpoint.
     */
    public Map<String, Integer> countVariableNodes(Collection<String> dataSourceIds) {
        return schemas.countVariableNodesBySource(dataSourceIds);
    }

    /**
     * Returns the VARIABLE node count for a single data-source (IS-149).
     */
    public int countVariableNodes(String dataSourceId) {
        return schemas.countVariableNodesBySource(List.of(dataSourceId))
                .getOrDefault(dataSourceId, 0);
    }

    public Schema save(String projectId, String dataSourceId, List<SchemaNode> nodes) {
        DataSourceRow source = requireSource(projectId, dataSourceId);
        validate(nodes);
        assertNoBreakingImpact(source, nodes);
        return map(schemas.saveNewVersion(dataSourceId, nodes));
    }

    private DataSourceRow requireSource(String projectId, String dataSourceId) {
        return dataSources.findById(dataSourceId)
                .filter(r -> r.projectId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("DataSource", dataSourceId));
    }

    /**
     * Rejects node removals still relied on by the source's own SYNTHETIC config (IS-094):
     * a synthetic variable generates values for a nodeId independent of the schema at runtime
     * (see SyntheticGenerator), so dropping the node would silently orphan that variable's
     * stream — no addressable node left for clients to read it from.
     */
    private void assertNoBreakingImpact(DataSourceRow source, List<SchemaNode> newNodes) {
        if (!"SYNTHETIC".equals(source.basis())) {
            return;
        }
        Optional<SchemaWithNodes> current = schemas.findCurrent(source.id());
        if (current.isEmpty()) {
            return;
        }
        Map<String, SchemaNode> newById = new HashMap<>();
        for (SchemaNode n : newNodes) {
            newById.put(n.nodeId(), n);
        }
        Set<String> brokenIds = new HashSet<>();
        for (SchemaNode old : current.get().nodes()) {
            SchemaNode kept = newById.get(old.nodeId());
            // Removed outright, or kept but no longer the VARIABLE/type a synthetic config could drive.
            if (kept == null || kept.kind() != old.kind() || kept.dataType() != old.dataType()) {
                brokenIds.add(old.nodeId());
            }
        }
        if (brokenIds.isEmpty()) {
            return;
        }
        List<String> issues = new ArrayList<>();
        for (String nodeId : configuredSyntheticNodeIds(source.runtimeConfig())) {
            if (brokenIds.contains(nodeId)) {
                issues.add("node '" + nodeId + "' is removed or retyped but still driven by this source's"
                        + " synthetic config");
            }
        }
        if (!issues.isEmpty()) {
            throw new SchemaImpactException(source.id(), issues);
        }
    }

    /** Mirrors the fail-loud convention of {@code SyntheticRunService.parseConfig}: an unparsable config is
     * a data problem, not a "nothing configured" no-op — silently ignoring it here would let a schema edit
     * remove a node the (corrupt) config still relies on. */
    private List<String> configuredSyntheticNodeIds(String runtimeConfigJson) {
        List<String> nodeIds = new ArrayList<>();
        if (runtimeConfigJson == null || runtimeConfigJson.isBlank()) {
            return nodeIds;
        }
        JsonNode root;
        try {
            root = json.readTree(runtimeConfigJson);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("invalid synthetic runtimeConfig: " + e.getMessage(), e);
        }
        JsonNode variables = root.isObject() ? root.get("variables") : null;
        if (variables != null && variables.isArray()) {
            for (JsonNode v : variables) {
                JsonNode nodeId = v.get("nodeId");
                if (nodeId != null && nodeId.isString()) {
                    nodeIds.add(nodeId.asString());
                }
            }
        }
        return nodeIds;
    }

    private static void validate(List<SchemaNode> nodes) {
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

    private Schema map(SchemaWithNodes s) {
        return new Schema(s.id(), s.dataSourceId(), s.version(), s.nodes(), s.createdAt().toInstant());
    }
}
