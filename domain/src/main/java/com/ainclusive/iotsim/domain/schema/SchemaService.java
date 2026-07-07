package com.ainclusive.iotsim.domain.schema;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaWithNodes;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Reads and saves the protocol-neutral schema of a data-source (backend-specs/01 & 05). */
@Service
public class SchemaService {

    private final SchemaRepository schemas;
    private final DataSourceRepository dataSources;

    public SchemaService(SchemaRepository schemas, DataSourceRepository dataSources) {
        this.schemas = schemas;
        this.dataSources = dataSources;
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
        requireSource(projectId, dataSourceId);
        validate(nodes);
        return map(schemas.saveNewVersion(dataSourceId, nodes));
    }

    private void requireSource(String projectId, String dataSourceId) {
        dataSources.findById(dataSourceId)
                .filter(r -> r.projectId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("DataSource", dataSourceId));
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
