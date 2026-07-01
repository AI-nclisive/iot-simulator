package com.ainclusive.iotsim.persistence.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Exercises the UserRepository (users + user_roles) against a real Postgres.
 * Skipped gracefully when no Docker daemon is available.
 */
@Testcontainers(disabledWithoutDocker = true)
class UserRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    static UserRepository repository;

    @BeforeAll
    static void migrateAndWire() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        DSLContext dsl = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        repository = new JooqUserRepository(dsl);
    }

    @Test
    void upsertCreatesNewUser() {
        UserRow user = repository.upsert("sub:alice", "Alice");
        assertThat(user.id()).isNotBlank();
        assertThat(user.subject()).isEqualTo("sub:alice");
        assertThat(user.displayName()).isEqualTo("Alice");
        assertThat(user.status()).isEqualTo("ACTIVE");
        assertThat(user.lastSeenAt()).isNotNull();
    }

    @Test
    void upsertUpdatesExistingUser() {
        UserRow first = repository.upsert("sub:bob", "Bob");
        UserRow second = repository.upsert("sub:bob", "Bob Updated");
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.displayName()).isEqualTo("Bob Updated");
    }

    @Test
    void findByIdAndBySubject() {
        UserRow user = repository.upsert("sub:carol", "Carol");
        assertThat(repository.findById(user.id())).isPresent();
        assertThat(repository.findBySubject("sub:carol")).isPresent();
        assertThat(repository.findBySubject("sub:nobody")).isEmpty();
    }

    @Test
    void findAllReturnsInsertedUsers() {
        repository.upsert("sub:dave", "Dave");
        List<UserRow> all = repository.findAll();
        assertThat(all).extracting(UserRow::subject).contains("sub:dave");
    }

    @Test
    void updateStatusChangesStatus() {
        UserRow user = repository.upsert("sub:eve", "Eve");
        Optional<UserRow> updated = repository.updateStatus(user.id(), "SUSPENDED");
        assertThat(updated).isPresent();
        assertThat(updated.get().status()).isEqualTo("SUSPENDED");
    }

    @Test
    void updateStatusReturnsEmptyForUnknownId() {
        Optional<UserRow> result = repository.updateStatus("non-existent-id", "SUSPENDED");
        assertThat(result).isEmpty();
    }

    @Test
    void assignAndRemoveRole() {
        UserRow user = repository.upsert("sub:frank", "Frank");
        repository.assignRole(user.id(), "user");
        assertThat(repository.findRoles(user.id())).containsExactlyInAnyOrder("user");

        // idempotent assign
        repository.assignRole(user.id(), "user");
        assertThat(repository.findRoles(user.id())).hasSize(1);

        repository.assignRole(user.id(), "admin");
        assertThat(repository.findRoles(user.id())).containsExactlyInAnyOrder("user", "admin");

        repository.removeRole(user.id(), "user");
        assertThat(repository.findRoles(user.id())).containsExactly("admin");

        // removing non-assigned role is a no-op
        repository.removeRole(user.id(), "user");
        assertThat(repository.findRoles(user.id())).containsExactly("admin");
    }

    @Test
    void deleteRemovesUser() {
        UserRow user = repository.upsert("sub:grace", "Grace");
        assertThat(repository.deleteById(user.id())).isTrue();
        assertThat(repository.findById(user.id())).isEmpty();
        assertThat(repository.deleteById(user.id())).isFalse();
    }
}
