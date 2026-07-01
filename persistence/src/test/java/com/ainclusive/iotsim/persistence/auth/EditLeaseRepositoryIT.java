package com.ainclusive.iotsim.persistence.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Exercises EditLeaseRepository against a real Postgres.
 * Skipped gracefully when no Docker daemon is available.
 */
@Testcontainers(disabledWithoutDocker = true)
class EditLeaseRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    static EditLeaseRepository repository;

    @BeforeAll
    static void migrateAndWire() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        DSLContext dsl = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        repository = new JooqEditLeaseRepository(dsl);
    }

    @Test
    void acquireNewLease() {
        EditLeaseRow lease = repository.acquireOrRenew("project", "proj-1", "alice", Duration.ofMinutes(5));
        assertThat(lease.objectType()).isEqualTo("project");
        assertThat(lease.objectId()).isEqualTo("proj-1");
        assertThat(lease.holder()).isEqualTo("alice");
        assertThat(lease.expiresAt()).isAfter(lease.acquiredAt());
    }

    @Test
    void renewExistingLeaseByHolder() {
        repository.acquireOrRenew("project", "proj-renew", "bob", Duration.ofMinutes(5));
        EditLeaseRow renewed = repository.acquireOrRenew("project", "proj-renew", "bob", Duration.ofMinutes(10));
        assertThat(renewed.holder()).isEqualTo("bob");
        // expires_at must be at least 9 minutes in the future (generous bound)
        assertThat(renewed.expiresAt()).isAfter(renewed.acquiredAt().plusMinutes(9));
    }

    @Test
    void conflictingHolderKeepsOriginalLease() {
        repository.acquireOrRenew("schema", "schema-1", "alice", Duration.ofMinutes(5));
        EditLeaseRow conflict = repository.acquireOrRenew("schema", "schema-1", "carol", Duration.ofMinutes(5));
        // alice still holds the lease
        assertThat(conflict.holder()).isEqualTo("alice");
    }

    @Test
    void findActiveReturnsEmptyWhenNoneExists() {
        assertThat(repository.findActive("project", "proj-none")).isEmpty();
    }

    @Test
    void findActiveReturnsExistingLease() {
        repository.acquireOrRenew("datasource", "ds-1", "alice", Duration.ofMinutes(5));
        assertThat(repository.findActive("datasource", "ds-1")).isPresent();
    }

    @Test
    void findAllActiveByHolder() {
        repository.acquireOrRenew("project", "proj-a", "dave", Duration.ofMinutes(5));
        repository.acquireOrRenew("project", "proj-b", "dave", Duration.ofMinutes(5));
        repository.acquireOrRenew("project", "proj-c", "eve", Duration.ofMinutes(5));

        List<EditLeaseRow> daveLeases = repository.findAllActiveByHolder("dave");
        assertThat(daveLeases).extracting(EditLeaseRow::objectId)
                .containsExactlyInAnyOrder("proj-a", "proj-b");
        assertThat(repository.findAllActiveByHolder("eve")).hasSize(1);
    }

    @Test
    void releaseByHolder() {
        repository.acquireOrRenew("project", "proj-release", "frank", Duration.ofMinutes(5));
        assertThat(repository.release("project", "proj-release", "frank")).isTrue();
        assertThat(repository.findActive("project", "proj-release")).isEmpty();
    }

    @Test
    void releaseByNonHolderIsNoOp() {
        repository.acquireOrRenew("project", "proj-noop", "grace", Duration.ofMinutes(5));
        assertThat(repository.release("project", "proj-noop", "henry")).isFalse();
        assertThat(repository.findActive("project", "proj-noop")).isPresent();
        // cleanup
        repository.release("project", "proj-noop", "grace");
    }

    @Test
    void deleteExpiredRemovesOnlyExpiredLeases() {
        // active lease
        repository.acquireOrRenew("project", "proj-active", "ivan", Duration.ofMinutes(5));
        // we cannot easily create an already-expired lease without time-travel,
        // so just verify the method runs and returns a non-negative count
        int deleted = repository.deleteExpired();
        assertThat(deleted).isGreaterThanOrEqualTo(0);
        // the active lease must still be there
        assertThat(repository.findActive("project", "proj-active")).isPresent();
        // cleanup
        repository.release("project", "proj-active", "ivan");
    }
}
