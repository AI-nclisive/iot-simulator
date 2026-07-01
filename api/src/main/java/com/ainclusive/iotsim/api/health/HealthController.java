package com.ainclusive.iotsim.api.health;

import com.ainclusive.iotsim.api.security.Permission;
import com.ainclusive.iotsim.platform.runtime.HealthOrigin;
import com.ainclusive.iotsim.platform.runtime.RuntimeController;
import com.ainclusive.iotsim.platform.runtime.SourceError;
import com.ainclusive.iotsim.platform.runtime.SourceHealth;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Per-source health & error surfacing (IS-053): the current runtime state plus
 * the most recent error (origin + reason + time), retained after recovery. Live
 * transitions are on the SSE runtime stream
 * ({@code GET /api/v1/projects/{pid}/stream/runtime}); this is the point-in-time
 * query. An unknown {@code id} returns 200 with {@code STOPPED} + no error,
 * mirroring the sibling observability endpoints. See
 * backend-specs/05_API_CONTRACT.md and SPEC.md → Observe Data Source Health And
 * Errors.
 *
 * <p>Authorization (IS-077): read-only — {@link Permission#OBSERVE} (user + admin).
 */
@RestController
@Tag(name = "Source Health", description = "Point-in-time runtime state and most-recent error for a single"
        + " data source. Live transitions arrive on the runtime SSE stream.")
public class HealthController {

    private static final String OBSERVE =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).OBSERVE)";

    private final RuntimeController runtime;

    public HealthController(RuntimeController runtime) {
        this.runtime = runtime;
    }

    @Operation(summary = "Get source health",
            description = "Returns the current runtime state and most-recent error for a data source."
                    + " An unknown id returns 200 with STOPPED and no error.")
    @PreAuthorize(OBSERVE)
    @GetMapping("/api/v1/data-sources/{id}/health")
    public SourceHealthResponse health(@PathVariable String id) {
        return SourceHealthResponse.from(runtime.health(id));
    }

    /** Current runtime state plus the most recent error (null when none). */
    public record SourceHealthResponse(String state, SourceErrorDto lastError) {
        static SourceHealthResponse from(SourceHealth h) {
            return new SourceHealthResponse(h.state(), SourceErrorDto.from(h.lastError()));
        }
    }

    /** Where a problem came from, why, and when. */
    public record SourceErrorDto(HealthOrigin origin, String reason, Instant at) {
        static SourceErrorDto from(SourceError e) {
            return e == null ? null : new SourceErrorDto(e.origin(), e.reason(), e.at());
        }
    }
}
