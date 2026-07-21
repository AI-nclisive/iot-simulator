package com.ainclusive.iotsim.domain.synthetic;

import com.ainclusive.iotsim.domain.datasource.DataSource;
import com.ainclusive.iotsim.domain.datasource.DataSourceService;
import com.ainclusive.iotsim.domain.manualschema.ManualSchemaService;
import com.ainclusive.iotsim.domain.schema.SchemaService;
import com.ainclusive.iotsim.protocolmodel.Access;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import com.ainclusive.iotsim.protocolmodel.ValueRank;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Create-from-synthetic (IS-065): builds a {@code SYNTHETIC} data source from a
 * synthetic setup and stores the serialized config in {@code runtimeConfig}. The
 * generated twin of {@code ScanService.createFromScan} (SPEC "Generate Synthetic Data" /
 * "Manually Create Data Source Schemas").
 *
 * <p>Three schema origins (IS-145, IS-173):
 * <ul>
 *   <li><b>derive</b> (default): one VARIABLE node per config variable, schema built from the
 *       variables themselves;
 *   <li><b>reuse an existing source</b> ({@code schemaFromSourceId} set): copy an existing
 *       source's full schema verbatim (names/paths/units/hierarchy) and only drive the nodes
 *       named by the config — so a synthetic twin looks identical to the scanned/imported
 *       source it mirrors;
 *   <li><b>reuse a manual schema</b> ({@code manualSchemaId} set): same copy-by-snapshot
 *       behavior, sourced from a standalone {@code ManualSchema} instead of another source's
 *       live schema — a later edit to the manual schema never affects this already-created
 *       source. Mutually exclusive with {@code schemaFromSourceId}.
 * </ul>
 */
@Service
public class SyntheticSourceService {

    private final DataSourceService dataSources;
    private final SchemaService schemas;
    private final ManualSchemaService manualSchemas;
    private final ObjectMapper json;

    public SyntheticSourceService(DataSourceService dataSources, SchemaService schemas,
            ManualSchemaService manualSchemas, ObjectMapper json) {
        this.dataSources = dataSources;
        this.schemas = schemas;
        this.manualSchemas = manualSchemas;
        this.json = json;
    }

    public DataSource create(String projectId, String name, String protocol, Integer simulatorPort,
            SyntheticConfig config, String schemaFromSourceId, String manualSchemaId, String actor) {
        if (config == null) {
            throw new IllegalArgumentException("config is required");
        }
        boolean reuseSource = schemaFromSourceId != null && !schemaFromSourceId.isBlank();
        boolean reuseManualSchema = manualSchemaId != null && !manualSchemaId.isBlank();
        if (reuseSource && reuseManualSchema) {
            throw new IllegalArgumentException("schemaFromSourceId and manualSchemaId are mutually exclusive");
        }
        // Validate the whole config up front (patterns, types, rates) before any write.
        SyntheticConfigMapper.toVariables(config);
        List<SchemaNode> nodes;
        if (reuseSource) {
            nodes = copiedSchemaNodes(schemas.get(projectId, schemaFromSourceId).nodes(), schemaFromSourceId, config);
        } else if (reuseManualSchema) {
            nodes = copiedSchemaNodes(
                    manualSchemas.get(projectId, manualSchemaId).nodes(), manualSchemaId, config);
        } else {
            nodes = schemaNodes(config);
        }
        // initialNodes=null: keep the two-step (create, then save schema) like ScanService;
        // DataSourceService.create gained an atomic initialNodes arg (IS-067) we don't use here.
        DataSource created = dataSources.create(
                projectId, name, protocol, "SYNTHETIC", simulatorPort, null,
                json.writeValueAsString(config), null, null, null, actor);
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

    /**
     * Full copy of an existing schema's nodes (IS-145, IS-173) — from either a data source's live
     * schema or a standalone {@code ManualSchema}. Every config variable must name a VARIABLE node
     * in that node set and match its data type; the returned node list is the source verbatim (all
     * nodes, so hierarchy/parent links stay intact).
     *
     * @param sourceLabel identifies the schema origin in error messages (a data-source id or a
     *     manual-schema id)
     */
    private static List<SchemaNode> copiedSchemaNodes(
            List<SchemaNode> sourceNodes, String sourceLabel, SyntheticConfig config) {
        Map<String, SchemaNode> variablesById = new LinkedHashMap<>();
        for (SchemaNode node : sourceNodes) {
            if (node.kind() == NodeKind.VARIABLE) {
                variablesById.put(node.nodeId(), node);
            }
        }
        for (SyntheticVariableConfig v : config.variables()) {
            SchemaNode node = variablesById.get(v.nodeId());
            if (node == null) {
                throw new IllegalArgumentException(
                        "variable nodeId '" + v.nodeId() + "' is not a VARIABLE node in the schema of "
                                + sourceLabel);
            }
            if (node.dataType() != v.dataType()) {
                throw new IllegalArgumentException(
                        "variable '" + v.nodeId() + "' data type " + v.dataType()
                                + " does not match schema node type " + node.dataType());
            }
        }
        return sourceNodes;
    }
}
