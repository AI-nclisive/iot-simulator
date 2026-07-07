package com.ainclusive.iotsim.api.recording;

import com.ainclusive.iotsim.api.security.Permission;
import com.ainclusive.iotsim.domain.synthetic.RecordingProfile;
import com.ainclusive.iotsim.domain.synthetic.RecordingProfiler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Derive a synthetic profile from a recording (IS-146): computes per-node statistics over the
 * recording's captured values and fits each measurement to a synthetic pattern, returning a
 * ready-to-use {@link SyntheticConfig} (preview). Used by the "Prefill from recording" control in
 * synthetic authoring; the client maps the returned per-node patterns onto its measurement rows.
 *
 * <p>Returns a per-measurement statistical profile with suggested params for every pattern type
 * and a recommended default; the client applies the recommendation and can swap to another type's
 * suggestion.
 *
 * <p>Authorization (IS-077): deriving is part of creating a synthetic source (admin-level) —
 * {@link Permission#SOURCE_EDIT}.
 */
@RestController
@Tag(name = "Recordings")
@RequestMapping("/api/v1/projects/{projectId}/recordings/{recordingId}/derive-synthetic")
public class RecordingDeriveController {

    private static final String SOURCE_EDIT =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).SOURCE_EDIT)";

    private final RecordingProfiler profiler;

    public RecordingDeriveController(RecordingProfiler profiler) {
        this.profiler = profiler;
    }

    @Operation(
            summary = "Derive a synthetic profile from a recording",
            description = "Computes per-measurement statistics over the recording's captured values and"
                    + " suggests ranges for every pattern type plus a recommended default. Returns a"
                    + " RecordingProfile the client applies to a synthetic source.")
    @PostMapping
    @PreAuthorize(SOURCE_EDIT)
    public RecordingProfile derive(
            @PathVariable String projectId, @PathVariable String recordingId) {
        return profiler.deriveProfile(projectId, recordingId);
    }
}
