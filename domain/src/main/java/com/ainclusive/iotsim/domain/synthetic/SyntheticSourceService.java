package com.ainclusive.iotsim.domain.synthetic;

import com.ainclusive.iotsim.domain.datasource.DataSource;
import com.ainclusive.iotsim.domain.datasource.DataSourceService;
import com.ainclusive.iotsim.domain.schema.SchemaService;
import com.ainclusive.iotsim.protocolmodel.Access;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import com.ainclusive.iotsim.protocolmodel.ValueRank;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Create-from-synthetic (IS-065): builds a {@code SYNTHETIC} data source from a
 * synthetic setup — derives the schema from the variables and stores the serialized
 * config in {@code runtimeConfig}. The generated twin of {@code ScanService.createFromScan}
 * (SPEC "Generate Synthetic Data" / "Manually Create Data Source Schemas").
 */
@Service
public class SyntheticSourceService {

    private final DataSourceService dataSources;
    private final SchemaService schemas;
    private final ObjectMapper json;

    public SyntheticSourceService(DataSourceService dataSources, SchemaService schemas, ObjectMapper json) {
        this.dataSources = dataSources;
        this.schemas = schemas;
        this.json = json;
    }

    public DataSource create(String projectId, String name, String protocol, Integer simulatorPort,
            SyntheticConfig config, String actor) {
        if (config == null) {
            throw new IllegalArgumentException("config is required");
        }
        // Validate the whole config up front (patterns, types, rates) before any write.
        SyntheticConfigMapper.toVariables(config);
        List<SchemaNode> nodes = schemaNodes(config);
        // initialNodes=null: keep the two-step (create, then save schema) like ScanService;
        // DataSourceService.create gained an atomic initialNodes arg (IS-067) we don't use here.
        DataSource created = dataSources.create(
                projectId, name, protocol, "SYNTHETIC", simulatorPort, null,
                json.writeValueAsString(config), null, null, actor);
        schemas.save(projectId, created.id(), nodes);
        // Re-read so the response carries the linked schemaId/schemaVersion.
        return dataSources.get(projectId, created.id());
    }

    /** One VARIABLE node per synthetic variable; path derived from the (unique) nodeId. */
    private static List<SchemaNode> schemaNodes(SyntheticConfig config) {
        List<SchemaNode> nodes = new ArrayList<>();
        for (SyntheticVariableConfig v : config.variables()) {
            nodes.add(new SchemaNode(v.nodeId(), null, "/" + v.nodeId(), v.nodeId(),
                    NodeKind.VARIABLE, v.dataType(), ValueRank.SCALAR, Access.READ, null, null));
        }
        return nodes;
    }
}
