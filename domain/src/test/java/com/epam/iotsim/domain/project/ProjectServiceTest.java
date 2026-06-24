package com.epam.iotsim.domain.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.epam.iotsim.domain.common.ConcurrencyConflictException;
import com.epam.iotsim.domain.common.ResourceNotFoundException;
import com.epam.iotsim.persistence.project.ProjectRepository;
import com.epam.iotsim.persistence.project.ProjectRow;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProjectServiceTest {

    private ProjectService service;

    @BeforeEach
    void setUp() {
        service = new ProjectService(new InMemoryProjectRepository());
    }

    @Test
    void createReturnsActiveProjectAtVersionZero() {
        Project p = service.create("Line 1", "desc", "alice");
        assertThat(p.id()).isNotBlank();
        assertThat(p.name()).isEqualTo("Line 1");
        assertThat(p.status()).isEqualTo(Project.ProjectStatus.ACTIVE);
        assertThat(p.version()).isZero();
        assertThat(p.createdBy()).isEqualTo("alice");
    }

    @Test
    void getMissingThrowsNotFound() {
        assertThatThrownBy(() -> service.get("nope"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateWithCurrentVersionIncrementsVersion() {
        Project p = service.create("a", null, "local");
        Project updated = service.update(p.id(), "b", "changed", p.version());
        assertThat(updated.name()).isEqualTo("b");
        assertThat(updated.version()).isEqualTo(1L);
    }

    @Test
    void updateWithStaleVersionThrowsConflict() {
        Project p = service.create("a", null, "local");
        assertThatThrownBy(() -> service.update(p.id(), "b", null, p.version() + 99))
                .isInstanceOf(ConcurrencyConflictException.class);
    }

    @Test
    void updateMissingThrowsNotFound() {
        assertThatThrownBy(() -> service.update("nope", "b", null, 0))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteMissingThrowsNotFound() {
        assertThatThrownBy(() -> service.delete("nope"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /** Minimal in-memory repository — keeps the service test free of a database. */
    private static final class InMemoryProjectRepository implements ProjectRepository {
        private final List<ProjectRow> rows = new ArrayList<>();
        private int seq;

        @Override
        public ProjectRow insert(String name, String description, String createdBy) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            ProjectRow row = new ProjectRow(
                    "id-" + (++seq), name, description, "ACTIVE", now, now, createdBy, 0);
            rows.add(row);
            return row;
        }

        @Override
        public Optional<ProjectRow> findById(String id) {
            return rows.stream().filter(r -> r.id().equals(id)).findFirst();
        }

        @Override
        public List<ProjectRow> findAll() {
            return List.copyOf(rows);
        }

        @Override
        public Optional<ProjectRow> update(String id, String name, String description, long expectedVersion) {
            for (int i = 0; i < rows.size(); i++) {
                ProjectRow r = rows.get(i);
                if (r.id().equals(id) && r.version() == expectedVersion) {
                    ProjectRow updated = new ProjectRow(
                            id, name, description, r.status(), r.createdAt(),
                            OffsetDateTime.now(ZoneOffset.UTC), r.createdBy(), r.version() + 1);
                    rows.set(i, updated);
                    return Optional.of(updated);
                }
            }
            return Optional.empty();
        }

        @Override
        public boolean deleteById(String id) {
            return rows.removeIf(r -> r.id().equals(id));
        }
    }
}
