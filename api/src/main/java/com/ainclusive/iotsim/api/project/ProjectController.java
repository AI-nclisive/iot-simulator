package com.ainclusive.iotsim.api.project;

import com.ainclusive.iotsim.api.error.PreconditionRequiredException;
import com.ainclusive.iotsim.domain.project.Project;
import com.ainclusive.iotsim.domain.project.ProjectService;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Projects resource. Path-based versioning (/api/v1); optimistic concurrency via
 * ETag / If-Match (decision D4). See backend-specs/05_API_CONTRACT.md.
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projects;

    public ProjectController(ProjectService projects) {
        this.projects = projects;
    }

    @GetMapping
    public List<ProjectResponse> list() {
        return projects.list().stream().map(ProjectResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@RequestBody CreateProjectRequest req) {
        if (req == null || req.name() == null || req.name().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        Project p = projects.create(req.name(), req.description(), "local");
        return ResponseEntity.created(URI.create("/api/v1/projects/" + p.id()))
                .eTag(etag(p.version()))
                .body(ProjectResponse.from(p));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> get(@PathVariable String id) {
        Project p = projects.get(id);
        return ResponseEntity.ok().eTag(etag(p.version())).body(ProjectResponse.from(p));
    }

    @PutMapping("/{id}")
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

    @DeleteMapping("/{id}")
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
