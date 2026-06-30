package com.ainclusive.iotsim.persistence.project;

import java.util.List;
import java.util.Optional;

/** CRUD over {@code projects} with optimistic concurrency (decision D4). */
public interface ProjectRepository {

    ProjectRow insert(String name, String description, String createdBy);

    Optional<ProjectRow> findById(String id);

    List<ProjectRow> findAll();

    /**
     * Optimistic update: applies only when the stored version matches
     * {@code expectedVersion}. Returns the new row, or empty when no row matched
     * (caller distinguishes not-found vs. version conflict).
     */
    Optional<ProjectRow> update(String id, String name, String description, long expectedVersion);

    /** Sets status to {@code ARCHIVED}. Returns the updated row, or empty when not found. */
    Optional<ProjectRow> archive(String id);

    boolean deleteById(String id);
}
