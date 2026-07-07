package com.ainclusive.iotsim.api.synthetic;

import com.ainclusive.iotsim.api.datasource.DataSourceController.DataSourceResponse;
import com.ainclusive.iotsim.api.security.Permission;
import com.ainclusive.iotsim.domain.datasource.DataSource;
import com.ainclusive.iotsim.domain.synthetic.SyntheticConfig;
import com.ainclusive.iotsim.domain.synthetic.SyntheticSourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Create-from-synthetic data sources (backend-specs/05; IS-065).
 *
 * <p>Authorization (IS-077): creating a synthetic source is admin-level —
 * {@link Permission#SOURCE_EDIT}.
 */
@RestController
@Tag(name = "Data Sources")
@RequestMapping("/api/v1/projects/{projectId}/data-sources/synthetic")
public class SyntheticSourceController {

    private static final String SOURCE_EDIT =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).SOURCE_EDIT)";

    private final SyntheticSourceService syntheticSources;

    public SyntheticSourceController(SyntheticSourceService syntheticSources) {
        this.syntheticSources = syntheticSources;
    }

    @Operation(
            summary = "Create a synthetic data source",
            description = "Creates a data source backed by a synthetic value profile from the supplied config."
                    + " Returns 201 Created with a Location header and the current ETag.")
    @PostMapping
    @PreAuthorize(SOURCE_EDIT)
    public ResponseEntity<DataSourceResponse> create(
            @PathVariable String projectId, @RequestBody CreateSyntheticSourceRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (req.protocol() == null || req.protocol().isBlank()) {
            throw new IllegalArgumentException("protocol is required");
        }
        if (req.config() == null) {
            throw new IllegalArgumentException("config is required");
        }
        DataSource ds = syntheticSources.create(
                projectId, req.name(), req.protocol(), req.simulatorPort(), req.config(),
                req.schemaFromSourceId(), "local");
        return ResponseEntity.created(
                        URI.create("/api/v1/projects/" + projectId + "/data-sources/" + ds.id()))
                .eTag("\"" + ds.version() + "\"")
                .body(DataSourceResponse.from(ds));
    }

    /**
     * {@code schemaFromSourceId} (optional, IS-145): reuse an existing source's schema verbatim
     * (names/paths/units) and drive the nodes named in {@code config}; when null the schema is
     * derived from the config variables.
     */
    public record CreateSyntheticSourceRequest(
            String name, String protocol, Integer simulatorPort, SyntheticConfig config,
            String schemaFromSourceId) {}
}
