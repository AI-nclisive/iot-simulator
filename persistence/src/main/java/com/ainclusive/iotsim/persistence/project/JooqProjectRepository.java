package com.ainclusive.iotsim.persistence.project;

import static com.ainclusive.iotsim.persistence.jooq.tables.Projects.PROJECTS;

import com.ainclusive.iotsim.persistence.jooq.tables.records.ProjectsRecord;
import com.ainclusive.iotsim.platform.Ids;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class JooqProjectRepository implements ProjectRepository {

    private final DSLContext dsl;

    public JooqProjectRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public ProjectRow insert(String name, String description, String createdBy) {
        ProjectsRecord record = dsl.insertInto(PROJECTS)
                .set(PROJECTS.ID, Ids.newId())
                .set(PROJECTS.NAME, name)
                .set(PROJECTS.DESCRIPTION, description)
                .set(PROJECTS.CREATED_BY, createdBy)
                .returning()
                .fetchOne();
        return map(record);
    }

    @Override
    public Optional<ProjectRow> findById(String id) {
        return dsl.selectFrom(PROJECTS)
                .where(PROJECTS.ID.eq(id))
                .fetchOptional()
                .map(this::map);
    }

    @Override
    public List<ProjectRow> findAll() {
        return dsl.selectFrom(PROJECTS)
                .orderBy(PROJECTS.CREATED_AT.desc())
                .fetch()
                .map(this::map);
    }

    @Override
    public List<ProjectRow> findAllPaged(String status, OffsetDateTime afterAt, String afterId, int limit) {
        var q = dsl.selectFrom(PROJECTS).where(org.jooq.impl.DSL.noCondition());
        if (status != null) {
            q = q.and(PROJECTS.STATUS.eq(status));
        }
        if (afterAt != null) {
            q = q.and(PROJECTS.CREATED_AT.lt(afterAt)
                    .or(PROJECTS.CREATED_AT.eq(afterAt).and(PROJECTS.ID.lt(afterId))));
        }
        return q.orderBy(PROJECTS.CREATED_AT.desc(), PROJECTS.ID.desc())
                .limit(limit)
                .fetch()
                .map(this::map);
    }

    @Override
    public Optional<ProjectRow> update(String id, String name, String description, long expectedVersion) {
        ProjectsRecord record = dsl.update(PROJECTS)
                .set(PROJECTS.NAME, name)
                .set(PROJECTS.DESCRIPTION, description)
                .set(PROJECTS.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .set(PROJECTS.VERSION, PROJECTS.VERSION.plus(1))
                .where(PROJECTS.ID.eq(id).and(PROJECTS.VERSION.eq(expectedVersion)))
                .returning()
                .fetchOne();
        return Optional.ofNullable(record).map(this::map);
    }

    @Override
    public Optional<ProjectRow> archive(String id) {
        ProjectsRecord record = dsl.update(PROJECTS)
                .set(PROJECTS.STATUS, "ARCHIVED")
                .set(PROJECTS.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .set(PROJECTS.VERSION, PROJECTS.VERSION.plus(1))
                .where(PROJECTS.ID.eq(id))
                .returning()
                .fetchOne();
        return Optional.ofNullable(record).map(this::map);
    }

    @Override
    public boolean deleteById(String id) {
        return dsl.deleteFrom(PROJECTS).where(PROJECTS.ID.eq(id)).execute() > 0;
    }

    private ProjectRow map(ProjectsRecord r) {
        return new ProjectRow(
                r.getId(),
                r.getName(),
                r.getDescription(),
                r.getStatus(),
                r.getCreatedAt(),
                r.getUpdatedAt(),
                r.getCreatedBy(),
                r.getVersion());
    }
}
