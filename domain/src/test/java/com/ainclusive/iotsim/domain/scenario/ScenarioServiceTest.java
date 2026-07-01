package com.ainclusive.iotsim.domain.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.domain.common.ConcurrencyConflictException;
import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.persistence.project.ProjectRepository;
import com.ainclusive.iotsim.persistence.project.ProjectRow;
import com.ainclusive.iotsim.persistence.scenario.ScenarioRepository;
import com.ainclusive.iotsim.persistence.scenario.ScenarioRow;
import com.ainclusive.iotsim.persistence.scenario.ScenarioStepInput;
import com.ainclusive.iotsim.persistence.scenario.ScenarioStepRow;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ScenarioServiceTest {

    private static final String PROJECT = "proj-1";

    private ScenarioService service;

    @BeforeEach
    void setUp() {
        service = new ScenarioService(
                new InMemoryScenarioRepository(),
                new FakeProjectRepository(Set.of(PROJECT)),
                new ObjectMapper());
    }

    private static ScenarioStep step(String type, String target, String params) {
        return new ScenarioStep(0, type, target, params);
    }

    @Test
    void createUnderExistingProjectNormalizesOrdinalsAndIsDraft() {
        Scenario s = service.create(PROJECT, "Flow", "{}",
                List.of(step("START", "ds-1", "{}"), step("STOP", "ds-1", "{}")), "alice");
        assertThat(s.id()).isNotBlank();
        assertThat(s.status()).isEqualTo("DRAFT");
        assertThat(s.createdBy()).isEqualTo("alice");
        assertThat(s.steps()).extracting(ScenarioStep::ordinal).containsExactly(0, 1);
    }

    @Test
    void createUnderMissingProjectThrowsNotFound() {
        assertThatThrownBy(() -> service.create("nope", "X", "{}", List.of(), "a"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createWithBlankNameThrows() {
        assertThatThrownBy(() -> service.create(PROJECT, "  ", "{}", List.of(), "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createWithUnknownStepTypeThrows() {
        assertThatThrownBy(() -> service.create(PROJECT, "X", "{}", List.of(step("NOPE", null, "{}")), "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createStartStepWithoutTargetThrows() {
        assertThatThrownBy(() -> service.create(PROJECT, "X", "{}", List.of(step("START", null, "{}")), "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createWithNonObjectParamsThrows() {
        assertThatThrownBy(() -> service.create(PROJECT, "X", "{}", List.of(step("WAIT", null, "[1,2]")), "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createWithNullParamsDefaultsToEmptyObject() {
        Scenario s = service.create(PROJECT, "X", null, List.of(step("MARKER", null, null)), "a");
        assertThat(s.deterministicSettings()).isEqualTo("{}");
        assertThat(s.steps().get(0).params()).isEqualTo("{}");
    }

    @Test
    void getFromWrongProjectThrowsNotFound() {
        Scenario s = service.create(PROJECT, "Mine", "{}", List.of(), "a");
        assertThatThrownBy(() -> service.get("other", s.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateWithNullStepsKeepsStepsAndRenames() {
        Scenario s = service.create(PROJECT, "N", "{}", List.of(step("MARKER", null, "{}")), "a");
        Scenario u = service.update(PROJECT, s.id(), "N2", null, null, s.version());
        assertThat(u.name()).isEqualTo("N2");
        assertThat(u.steps()).extracting(ScenarioStep::type).containsExactly("MARKER");
        assertThat(u.version()).isEqualTo(s.version() + 1);
    }

    @Test
    void updateWithStaleVersionThrowsConflict() {
        Scenario s = service.create(PROJECT, "N", "{}", List.of(), "a");
        assertThatThrownBy(() -> service.update(PROJECT, s.id(), "X", null, null, s.version() + 5))
                .isInstanceOf(ConcurrencyConflictException.class);
    }

    @Test
    void updateMissingThrowsNotFound() {
        assertThatThrownBy(() -> service.update(PROJECT, "nope", "X", null, null, 0))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void duplicateCopiesStepsAsDraftWithSuffix() {
        Scenario s = service.create(PROJECT, "Orig", "{\"seed\":1}",
                List.of(step("START", "ds-1", "{}")), "a");
        Scenario copy = service.duplicate(PROJECT, s.id(), "bob");
        assertThat(copy.id()).isNotEqualTo(s.id());
        assertThat(copy.name()).isEqualTo("Orig (copy)");
        assertThat(copy.status()).isEqualTo("DRAFT");
        assertThat(copy.createdBy()).isEqualTo("bob");
        assertThat(copy.steps()).extracting(ScenarioStep::type).containsExactly("START");
        assertThat(copy.deterministicSettings()).contains("seed");
    }

    @Test
    void deleteRemovesScenario() {
        Scenario s = service.create(PROJECT, "D", "{}", List.of(), "a");
        service.delete(PROJECT, s.id());
        assertThatThrownBy(() -> service.get(PROJECT, s.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- fakes ----

    private static final class InMemoryScenarioRepository implements ScenarioRepository {
        private final Map<String, ScenarioRow> rows = new HashMap<>();
        private int seq;

        @Override
        public ScenarioRow create(String projectId, String name, String deterministicSettings,
                List<ScenarioStepInput> steps, String createdBy) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            ScenarioRow row = new ScenarioRow("scn-" + (++seq), projectId, name, "DRAFT",
                    deterministicSettings != null ? deterministicSettings : "{}",
                    toRows(steps), now, now, createdBy, 0);
            rows.put(row.id(), row);
            return row;
        }

        @Override
        public Optional<ScenarioRow> findById(String id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public List<ScenarioRow> findByProject(String projectId) {
            return rows.values().stream().filter(r -> r.projectId().equals(projectId)).toList();
        }

        @Override
        public List<ScenarioRow> findByProjectPaged(String projectId,
                OffsetDateTime afterAt, String afterId, int limit) {
            return findByProject(projectId).stream().limit(limit).toList();
        }

        @Override
        public Optional<ScenarioRow> update(String id, String name, String deterministicSettings,
                List<ScenarioStepInput> steps, long expectedVersion) {
            ScenarioRow cur = rows.get(id);
            if (cur == null || cur.version() != expectedVersion) {
                return Optional.empty();
            }
            ScenarioRow next = new ScenarioRow(cur.id(), cur.projectId(),
                    name != null ? name : cur.name(), cur.status(),
                    deterministicSettings != null ? deterministicSettings : cur.deterministicSettings(),
                    steps != null ? toRows(steps) : cur.steps(),
                    cur.createdAt(), OffsetDateTime.now(ZoneOffset.UTC), cur.createdBy(),
                    cur.version() + 1);
            rows.put(id, next);
            return Optional.of(next);
        }

        @Override
        public boolean deleteById(String id) {
            return rows.remove(id) != null;
        }

        private static List<ScenarioStepRow> toRows(List<ScenarioStepInput> steps) {
            List<ScenarioStepRow> out = new ArrayList<>();
            for (int i = 0; i < steps.size(); i++) {
                ScenarioStepInput s = steps.get(i);
                out.add(new ScenarioStepRow(i, s.type(), s.targetSourceId(),
                        s.params() != null ? s.params() : "{}"));
            }
            return out;
        }
    }

    private static final class FakeProjectRepository implements ProjectRepository {
        private final Set<String> existing;

        FakeProjectRepository(Set<String> existing) {
            this.existing = existing;
        }

        @Override
        public Optional<ProjectRow> findById(String id) {
            if (!existing.contains(id)) {
                return Optional.empty();
            }
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            return Optional.of(new ProjectRow(id, "p", null, "ACTIVE", now, now, "local", 0));
        }

        @Override public ProjectRow insert(String n, String d, String by) { throw new UnsupportedOperationException(); }
        @Override public List<ProjectRow> findAll() { return List.of(); }
        @Override public List<ProjectRow> findAllPaged(String s, OffsetDateTime a, String i, int l) { return List.of(); }
        @Override public Optional<ProjectRow> update(String i, String n, String d, long v) { throw new UnsupportedOperationException(); }
        @Override public Optional<ProjectRow> archive(String i) { throw new UnsupportedOperationException(); }
        @Override public boolean deleteById(String i) { throw new UnsupportedOperationException(); }
    }
}
