package com.ainclusive.iotsim.api.manualschema;

import com.ainclusive.iotsim.api.error.PreconditionRequiredException;
import com.ainclusive.iotsim.api.schema.ReferenceDto;
import com.ainclusive.iotsim.api.schema.SchemaReferenceMapper;
import com.ainclusive.iotsim.api.security.Permission;
import com.ainclusive.iotsim.domain.manualschema.ManualSchema;
import com.ainclusive.iotsim.domain.manualschema.ManualSchemaService;
import com.ainclusive.iotsim.domain.manualschema.OpcUaNodeSetImporter;
import com.ainclusive.iotsim.domain.support.Page;
import com.ainclusive.iotsim.protocolmodel.Access;
import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import com.ainclusive.iotsim.protocolmodel.ValueRank;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual schemas: a reusable, standalone structure library, not bound to any data-source
 * (backend-specs/05_API_CONTRACT.md §Manual schemas; SPEC "Manually Create Data Source Schemas").
 * Consumed only via a synthetic source's {@code manualSchemaId} (IS-173).
 *
 * <p>Authorization (IS-077): list/get — {@link Permission#OBSERVE}; create/update/delete/duplicate —
 * {@link Permission#SCHEMA_EDIT}.
 */
@RestController
@Tag(name = "Data Sources")
@RequestMapping("/api/v1/projects/{projectId}/manual-schemas")
public class ManualSchemaController {

    private static final String OBSERVE =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).OBSERVE)";
    private static final String SCHEMA_EDIT =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).SCHEMA_EDIT)";

    private final ManualSchemaService manualSchemas;

    public ManualSchemaController(ManualSchemaService manualSchemas) {
        this.manualSchemas = manualSchemas;
    }

    @Operation(summary = "List manual schemas",
            description = "Returns a page of manual schemas in the project using cursor-based pagination.")
    @GetMapping
    @PreAuthorize(OBSERVE)
    public Page<ManualSchemaResponse> list(
            @PathVariable String projectId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return manualSchemas.listPaged(projectId, cursor, limit).map(ManualSchemaResponse::from);
    }

    @Operation(
            summary = "Create a manual schema",
            description = "Creates a standalone, reusable manual schema in the project. Returns 201 Created"
                    + " with a Location header and the current ETag.")
    @PostMapping
    @PreAuthorize(SCHEMA_EDIT)
    public ResponseEntity<ManualSchemaResponse> create(
            @PathVariable String projectId, @RequestBody CreateManualSchemaRequest req) {
        require(req != null && notBlank(req.name()), "name is required");
        require(notBlank(req.protocol()), "protocol is required");
        List<SchemaNode> nodes = req.nodes() == null ? List.of() : toNodes(req.nodes());
        ManualSchema schema = manualSchemas.create(
                projectId, req.protocol(), req.name(), req.description(), nodes, "local");
        return ResponseEntity.created(
                        URI.create("/api/v1/projects/" + projectId + "/manual-schemas/" + schema.id()))
                .eTag(etag(schema.version()))
                .body(ManualSchemaResponse.from(schema));
    }

    @Operation(summary = "Import an OPC UA NodeSet XML file",
            description = "Imports supported Objects, Variables, Methods and references into a reusable manual"
                    + " schema. Unsupported definitions are returned as explicit diagnostics and are never"
                    + " silently flattened.")
    @PostMapping("/import-nodeset")
    @PreAuthorize(SCHEMA_EDIT)
    public ResponseEntity<ImportNodeSetResponse> importNodeSet(
            @PathVariable String projectId, @RequestBody ImportNodeSetRequest req) {
        require(req != null && notBlank(req.name()), "name is required");
        require(notBlank(req.xml()), "xml is required");
        OpcUaNodeSetImporter.Result imported = OpcUaNodeSetImporter.importXml(req.xml());
        ManualSchema schema = manualSchemas.create(
                projectId, "OPC_UA", req.name(), req.description(), imported.nodes(), "local");
        return ResponseEntity.created(
                        URI.create("/api/v1/projects/" + projectId + "/manual-schemas/" + schema.id()))
                .eTag(etag(schema.version()))
                .body(new ImportNodeSetResponse(ManualSchemaResponse.from(schema), imported.diagnostics()));
    }

    @Operation(summary = "Get a manual schema", description = "Returns a single manual schema by id, with its"
            + " current version as the ETag.")
    @GetMapping("/{id}")
    @PreAuthorize(OBSERVE)
    public ResponseEntity<ManualSchemaResponse> get(@PathVariable String projectId, @PathVariable String id) {
        ManualSchema schema = manualSchemas.get(projectId, id);
        return ResponseEntity.ok().eTag(etag(schema.version())).body(ManualSchemaResponse.from(schema));
    }

    @Operation(
            summary = "Save a manual schema in place",
            description = "Overwrites the manual schema's name/description/nodes on the same row (no version"
                    + " chain — use duplicate for save-as-new). Requires an If-Match header carrying the"
                    + " current version for optimistic concurrency.")
    @PutMapping("/{id}")
    @PreAuthorize(SCHEMA_EDIT)
    public ResponseEntity<ManualSchemaResponse> update(
            @PathVariable String projectId, @PathVariable String id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody UpdateManualSchemaRequest req) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException("If-Match header with the current version is required");
        }
        require(req != null && notBlank(req.name()), "name is required");
        List<SchemaNode> nodes = req.nodes() == null ? List.of() : toNodes(req.nodes());
        ManualSchema schema = manualSchemas.update(
                projectId, id, req.name(), req.description(), nodes, parseVersion(ifMatch));
        return ResponseEntity.ok().eTag(etag(schema.version())).body(ManualSchemaResponse.from(schema));
    }

    @Operation(
            summary = "Duplicate a manual schema (save-as-new)",
            description = "Creates a copy of the manual schema under a new name. Returns 201 Created with a"
                    + " Location header.")
    @PostMapping("/{id}/duplicate")
    @PreAuthorize(SCHEMA_EDIT)
    public ResponseEntity<ManualSchemaResponse> duplicate(
            @PathVariable String projectId, @PathVariable String id, @RequestBody DuplicateManualSchemaRequest req) {
        require(req != null && notBlank(req.name()), "name is required");
        ManualSchema schema = manualSchemas.duplicate(projectId, id, req.name(), "local");
        return ResponseEntity.created(
                        URI.create("/api/v1/projects/" + projectId + "/manual-schemas/" + schema.id()))
                .eTag(etag(schema.version()))
                .body(ManualSchemaResponse.from(schema));
    }

    @Operation(summary = "Delete a manual schema", description = "Deletes a manual schema and returns 204 No"
            + " Content. Already-created data sources copied from it are unaffected.")
    @DeleteMapping("/{id}")
    @PreAuthorize(SCHEMA_EDIT)
    public ResponseEntity<Void> delete(@PathVariable String projectId, @PathVariable String id) {
        manualSchemas.delete(projectId, id);
        return ResponseEntity.noContent().build();
    }

    private static List<SchemaNode> toNodes(List<NodeDto> dtos) {
        List<SchemaNode> nodes = new ArrayList<>(dtos.size());
        for (NodeDto d : dtos) {
            requireText(d.nodeId(), "nodeId");
            requireText(d.path(), "path");
            requireText(d.name(), "name");
            NodeKind kind = parseEnum(NodeKind.class, d.kind(), "kind");
            DataType dataType = d.dataType() == null ? null : parseEnum(DataType.class, d.dataType(), "dataType");
            ValueRank valueRank =
                    d.valueRank() == null ? null : parseEnum(ValueRank.class, d.valueRank(), "valueRank");
            Access access = d.access() == null ? null : parseEnum(Access.class, d.access(), "access");
            if (kind == NodeKind.VARIABLE && (dataType == null || valueRank == null || access == null)) {
                throw new IllegalArgumentException(
                        "variable node '" + d.path() + "' requires dataType, valueRank and access");
            }
            nodes.add(new SchemaNode(
                    d.nodeId(), d.parentId(), d.path(), d.name(),
                    kind, dataType, valueRank, access, d.unit(), d.description(), d.arrayDimensions(),
                    d.typeDefinition(), SchemaReferenceMapper.toModel(d.references())));
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

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String etag(long version) {
        return "\"" + version + "\"";
    }

    private static long parseVersion(String ifMatch) {
        String v = ifMatch.trim();
        if (v.startsWith("W/")) {
            v = v.substring(2);
        }
        v = v.replace("\"", "").trim();
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid If-Match version: " + ifMatch);
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

    public record CreateManualSchemaRequest(String protocol, String name, String description, List<NodeDto> nodes) {}

    public record ImportNodeSetRequest(String name, String description, String xml) {}

    public record ImportNodeSetResponse(
            ManualSchemaResponse schema, List<OpcUaNodeSetImporter.Diagnostic> diagnostics) {}

    public record UpdateManualSchemaRequest(String name, String description, List<NodeDto> nodes) {}

    public record DuplicateManualSchemaRequest(String name) {}

    public record ManualSchemaResponse(
            String id, String projectId, String protocol, String name, String description,
            List<NodeDto> nodes, long version) {

        static ManualSchemaResponse from(ManualSchema s) {
            return new ManualSchemaResponse(s.id(), s.projectId(), s.protocol(), s.name(), s.description(),
                    s.nodes().stream().map(NodeDto::from).toList(), s.version());
        }
    }
}
