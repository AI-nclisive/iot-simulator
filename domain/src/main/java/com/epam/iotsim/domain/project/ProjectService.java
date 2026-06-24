package com.epam.iotsim.domain.project;

import com.epam.iotsim.domain.common.ConcurrencyConflictException;
import com.epam.iotsim.domain.common.ResourceNotFoundException;
import com.epam.iotsim.domain.project.Project.ProjectStatus;
import com.epam.iotsim.persistence.project.ProjectRepository;
import com.epam.iotsim.persistence.project.ProjectRow;
import java.util.List;
import org.springframework.stereotype.Service;

/** Project lifecycle (backend-specs/03_DOMAIN_MODEL.md). */
@Service
public class ProjectService {

    private final ProjectRepository repository;

    public ProjectService(ProjectRepository repository) {
        this.repository = repository;
    }

    public Project create(String name, String description, String actor) {
        return toDomain(repository.insert(name, description, actor));
    }

    public List<Project> list() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    public Project get(String id) {
        return repository.findById(id).map(this::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));
    }

    public Project update(String id, String name, String description, long expectedVersion) {
        return repository.update(id, name, description, expectedVersion)
                .map(this::toDomain)
                .orElseGet(() -> {
                    // No row matched (id + version). Distinguish 404 from 409.
                    if (repository.findById(id).isEmpty()) {
                        throw new ResourceNotFoundException("Project", id);
                    }
                    throw new ConcurrencyConflictException("Project", id, expectedVersion);
                });
    }

    public void delete(String id) {
        if (!repository.deleteById(id)) {
            throw new ResourceNotFoundException("Project", id);
        }
    }

    private Project toDomain(ProjectRow r) {
        return new Project(
                r.id(),
                r.name(),
                r.description(),
                ProjectStatus.valueOf(r.status()),
                r.createdAt().toInstant(),
                r.updatedAt().toInstant(),
                r.createdBy(),
                r.version());
    }
}
