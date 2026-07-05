package com.ainclusive.iotsim.api.project;

import com.ainclusive.iotsim.api.error.PreconditionRequiredException;
import com.ainclusive.iotsim.api.security.Permission;
import com.ainclusive.iotsim.domain.project.Project;
import com.ainclusive.iotsim.domain.project.ProjectService;
import com.ainclusive.iotsim.domain.support.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.time.Instant;
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
 * Projects resource. Path-based versioning (/api/v1); optimistic concurrency via
 * ETag / If-Match (decision D4). See backend-specs/05_API_CONTRACT.md.
 *
 * <p>Authorization (IS-077, backend-specs/08 §Authorization):
 * <ul>
 *   <li>List / get — {@link Permission#OBSERVE} (user + admin).
 *   <li>Create / update / duplicate / archive / delete — {@link Permission#PROJECT_EDIT} (admin).
 * </ul>
 */
@RestController
@Tag(name = "Projects")
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private static final String OBSERVE =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).OBSERVE)";
    private static final String PROJECT_EDIT =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).PROJECT_EDIT)";

    private final ProjectService projects;

    public ProjectController(ProjectService projects) {
        this.projects = projects;
    }

    @Operation(summary = "List projects",
            description = "Returns a page of projects, optionally filtered by status."
                    + " Uses cursor-based pagination via the cursor and limit query parameters.")
    @GetMapping
    @PreAuthorize(OBSERVE)
    public Page<ProjectResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return projects.listPaged(status, cursor, limit)
                .map(ProjectResponse::from);
    }

    @Operation(summary = "Create a project",
            description = "Creates a new project from the supplied name and optional description."
                    + " Responds 201 Created with a Location header and an ETag for the new version.")
    @PostMapping
    @PreAuthorize(PROJECT_EDIT)
    public ResponseEntity<ProjectResponse> create(@RequestBody CreateProjectRequest req) {
        if (req == null || req.name() == null || req.name().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        Project p = projects.create(req.name(), req.description(), "local");
        return ResponseEntity.created(URI.create("/api/v1/projects/" + p.id()))
                .eTag(etag(p.version()))
                .body(ProjectResponse.from(p));
    }

    @Operation(summary = "Get a project",
            description = "Returns a single project by id, with an ETag carrying its current version.")
    @GetMapping("/{id}")
    @PreAuthorize(OBSERVE)
    public ResponseEntity<ProjectResponse> get(@PathVariable String id) {
        Project p = projects.get(id);
        return ResponseEntity.ok().eTag(etag(p.version())).body(ProjectResponse.from(p));
    }

    @Operation(summary = "Update a project",
            description = "Updates a project's name and description. Requires an If-Match header carrying"
                    + " the current version for optimistic concurrency; returns 428 when it is missing.")
    @PutMapping("/{id}")
    @PreAuthorize(PROJECT_EDIT)
    public ResponseEntity<ProjectResponse> update(
            @PathVariable String id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody UpdateProjectRequest req) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException("If-Match header with the current version is required");
        }
        Project p = projects.update(id, req.name(), req.description(), parseVersion(ifMatch));
        return ResponseEntity.ok().eTag(etag(p.version())).body(ProjectResponse.from(p));
    }

    @Operation(summary = "Duplicate a project",
            description = "Creates a copy of the project and its contents as a new project."
                    + " Responds 201 Created with a Location header and an ETag for the new version.")
    @PostMapping("/{id}/duplicate")
    @PreAuthorize(PROJECT_EDIT)
    public ResponseEntity<ProjectResponse> duplicate(@PathVariable String id) {
        Project p = projects.duplicate(id);
        return ResponseEntity.created(URI.create("/api/v1/projects/" + p.id()))
                .eTag(etag(p.version()))
                .body(ProjectResponse.from(p));
    }

    @Operation(summary = "Archive a project",
            description = "Marks the project as archived and returns it with an ETag for its new version.")
    @PostMapping("/{id}/archive")
    @PreAuthorize(PROJECT_EDIT)
    public ResponseEntity<ProjectResponse> archive(@PathVariable String id) {
        Project p = projects.archive(id);
        return ResponseEntity.ok().eTag(etag(p.version())).body(ProjectResponse.from(p));
    }

    @Operation(summary = "Delete a project",
            description = "Permanently deletes the project and its contents, responding 204 No Content.")
    @DeleteMapping("/{id}")
    @PreAuthorize(PROJECT_EDIT)
    public ResponseEntity<Void> delete(@PathVariable String id) {
        projects.delete(id);
        return ResponseEntity.noContent().build();
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

    public record CreateProjectRequest(String name, String description) {}

    public record UpdateProjectRequest(String name, String description) {}

    public record ProjectResponse(
            String id, String name, String description, String status,
            Instant createdAt, Instant updatedAt, String createdBy, long version) {

        static ProjectResponse from(Project p) {
            return new ProjectResponse(
                    p.id(), p.name(), p.description(), p.status().name(),
                    p.createdAt(), p.updatedAt(), p.createdBy(), p.version());
        }
    }
}
