package com.ainclusive.iotsim.api.recording;

import com.ainclusive.iotsim.api.recording.RecordingController.RecordingResponse;
import com.ainclusive.iotsim.api.security.Permission;
import com.ainclusive.iotsim.domain.recording.Recording;
import com.ainclusive.iotsim.domain.recording.RecordingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Live capture of real data into a recording (IS-045): start/stop recording a
 * running real source. Mirrors backend-specs/05_API_CONTRACT.md §Recordings
 * ({@code POST .../data-sources/{id}/recording/start|stop}). Start connects to the
 * source's real endpoint in client mode and streams observed value changes into a
 * new recording; stop ends the capture and finalizes the recording.
 *
 * <p>Authorization (IS-077): start/stop capture are runtime-operate actions —
 * {@link Permission#SOURCE_START} / {@link Permission#SOURCE_STOP} (user + admin).
 */
@RestController
@Tag(
        name = "Recording Capture",
        description =
                "Start and stop live capture of a running real source into a new recording.")
@RequestMapping("/api/v1/projects/{projectId}/data-sources/{dataSourceId}/recording")
public class RecordingCaptureController {

    private static final String SOURCE_START =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).SOURCE_START)";
    private static final String SOURCE_STOP =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).SOURCE_STOP)";

    private final RecordingService recordings;

    public RecordingCaptureController(RecordingService recordings) {
        this.recordings = recordings;
    }

    /** Starts capturing the real source into a new recording; 201 with the recording. */
    @Operation(
            summary = "Start capture",
            description =
                    "Connects to the source's real endpoint in client mode and streams observed"
                    + " value changes into a new recording. Returns 201 with the new recording.")
    @PostMapping("/start")
    @PreAuthorize(SOURCE_START)
    public ResponseEntity<RecordingResponse> start(
            @PathVariable String projectId, @PathVariable String dataSourceId) {
        Recording recording = recordings.startCapture(projectId, dataSourceId, "local");
        return ResponseEntity.created(
                        URI.create("/api/v1/projects/" + projectId + "/recordings/" + recording.id()))
                .eTag(etag(recording.version()))
                .body(RecordingResponse.from(recording));
    }

    /** Stops the active capture and finalizes its recording; 200 with the recording. */
    @Operation(
            summary = "Stop capture",
            description =
                    "Ends the active capture on the data source and finalizes its recording."
                    + " Returns 200 with the finalized recording.")
    @PostMapping("/stop")
    @PreAuthorize(SOURCE_STOP)
    public ResponseEntity<RecordingResponse> stop(
            @PathVariable String projectId, @PathVariable String dataSourceId) {
        Recording recording = recordings.stopCapture(projectId, dataSourceId);
        return ResponseEntity.ok().eTag(etag(recording.version())).body(RecordingResponse.from(recording));
    }

    private static String etag(long version) {
        return "\"" + version + "\"";
    }
}
