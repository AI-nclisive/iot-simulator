package com.ainclusive.iotsim.api.schema;

import com.ainclusive.iotsim.api.security.Permission;
import com.ainclusive.iotsim.domain.schema.Schema;
import com.ainclusive.iotsim.domain.schema.SchemaService;
import com.ainclusive.iotsim.protocolmodel.Access;
import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.ReferenceType;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import com.ainclusive.iotsim.protocolmodel.SchemaReference;
import com.ainclusive.iotsim.protocolmodel.ValueRank;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The protocol-neutral schema of a data-source. GET returns the current version;
 * PUT saves a new version (full-editor save). See backend-specs/05_API_CONTRACT.md.
 *
 * <p>Authorization (IS-077): GET — {@link Permission#OBSERVE}; PUT — {@link Permission#SCHEMA_EDIT}.
 */
@RestController
@Tag(name = "Data Sources")
@RequestMapping("/api/v1/projects/{projectId}/data-sources/{dataSourceId}/schema")
public class SchemaController {

    private static final String OBSERVE =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).OBSERVE)";
    private static final String SCHEMA_EDIT =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).SCHEMA_EDIT)";

    private final SchemaService schemas;

    public SchemaController(SchemaService schemas) {
        this.schemas = schemas;
    }

    @Operation(
            summary = "Get the source schema",
            description = "Returns the current value schema of the data source, with its version as the ETag.")
    @GetMapping
    @PreAuthorize(OBSERVE)
    public ResponseEntity<SchemaResponse> get(
            @PathVariable String projectId, @PathVariable String dataSourceId) {
        Schema schema = schemas.get(projectId, dataSourceId);
        return ResponseEntity.ok().eTag(etag(schema.version())).body(SchemaResponse.from(schema));
    }

    @Operation(
            summary = "Replace the source schema",
            description = "Replaces the data source's schema with the supplied nodes, saving a new version"
                    + " and returning the updated ETag.")
    @PutMapping
    @PreAuthorize(SCHEMA_EDIT)
    public ResponseEntity<SchemaResponse> save(
            @PathVariable String projectId, @PathVariable String dataSourceId,
            @RequestBody SaveSchemaRequest req) {
        if (req == null || req.nodes() == null) {
            throw new IllegalArgumentException("nodes are required");
        }
        Schema schema = schemas.save(projectId, dataSourceId, toNodes(req.nodes()));
        return ResponseEntity.ok().eTag(etag(schema.version())).body(SchemaResponse.from(schema));
    }

    private static String etag(int version) {
        return "\"" + version + "\"";
    }

    private static List<SchemaNode> toNodes(List<NodeDto> dtos) {
        List<SchemaNode> nodes = new ArrayList<>(dtos.size());
        for (NodeDto d : dtos) {
            requireText(d.nodeId(), "nodeId");
            requireText(d.path(), "path");
            requireText(d.name(), "name");
            NodeKind kind = parseEnum(NodeKind.class, d.kind(), "kind");
            DataType dataType = d.dataType() == null ? null : parseEnum(DataType.class, d.dataType(), "dataType");
            ValueRank valueRank = d.valueRank() == null ? null : parseEnum(ValueRank.class, d.valueRank(), "valueRank");
            Access access = d.access() == null ? null : parseEnum(Access.class, d.access(), "access");
            if (kind == NodeKind.VARIABLE && (dataType == null || valueRank == null || access == null)) {
                throw new IllegalArgumentException(
                        "variable node '" + d.path() + "' requires dataType, valueRank and access");
            }
            nodes.add(new SchemaNode(d.nodeId(), d.parentId(), d.path(), d.name(),
                    kind, dataType, valueRank, access, d.unit(), d.description(), d.arrayDimensions(),
                    d.typeDefinition(), references(d.references())));
        }
        return nodes;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("invalid " + field + ": " + value);
        }
    }

    public record NodeDto(
            String nodeId, String parentId, String path, String name, String kind,
            String dataType, String valueRank, String access, String unit, String description,
            List<Integer> arrayDimensions, String typeDefinition, List<ReferenceDto> references) {

        public NodeDto(String nodeId, String parentId, String path, String name, String kind,
                String dataType, String valueRank, String access, String unit, String description) {
            this(nodeId, parentId, path, name, kind, dataType, valueRank, access, unit, description,
                    List.of(), null, List.of());
        }

        static NodeDto from(SchemaNode n) {
            return new NodeDto(
                    n.nodeId(), n.parentId(), n.path(), n.name(), n.kind().name(),
                    n.dataType() == null ? null : n.dataType().name(),
                    n.valueRank() == null ? null : n.valueRank().name(),
                    n.access() == null ? null : n.access().name(),
                    n.unit(), n.description(), n.arrayDimensions(), n.typeDefinition(),
                    n.references().stream().map(ReferenceDto::from).toList());
        }
    }

    public record ReferenceDto(String targetNodeId, String type, boolean forward) {
        static ReferenceDto from(SchemaReference reference) {
            return new ReferenceDto(reference.targetNodeId(), reference.type().name(), reference.forward());
        }
    }

    private static List<SchemaReference> references(List<ReferenceDto> dtos) {
        if (dtos == null) return List.of();
        return dtos.stream().map(d -> new SchemaReference(d.targetNodeId(),
                parseEnum(ReferenceType.class, d.type(), "reference.type"), d.forward())).toList();
    }

    public record SaveSchemaRequest(List<NodeDto> nodes) {}

    public record SchemaResponse(
            String id, String dataSourceId, int version, List<NodeDto> nodes) {

        static SchemaResponse from(Schema s) {
            return new SchemaResponse(s.id(), s.dataSourceId(), s.version(),
                    s.nodes().stream().map(NodeDto::from).toList());
        }
    }
}
