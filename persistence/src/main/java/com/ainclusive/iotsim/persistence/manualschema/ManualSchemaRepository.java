package com.ainclusive.iotsim.persistence.manualschema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** CRUD over {@code manual_schemas} with optimistic concurrency (IS-171). */
public interface ManualSchemaRepository {

    ManualSchemaRow create(String projectId, String protocol, String name, String description,
            String nodesJson, String createdBy);

    List<ManualSchemaRow> findByProject(String projectId);

    /** Cursor-paged list (IS-074). Sort: {@code created_at DESC, id DESC}. */
    List<ManualSchemaRow> findByProjectPaged(String projectId, OffsetDateTime afterAt, String afterId, int limit);

    Optional<ManualSchemaRow> findById(String id);

    /** Save-in-place: overwrites name/description/nodes on the same row (no version chain). */
    Optional<ManualSchemaRow> update(String id, String name, String description,
            String nodesJson, long expectedVersion);

    /**
     * Save-as-new: creates a copy of an existing manual schema under the same project.
     * The copy gets a new ID, the supplied name, and version 0.
     * Returns an empty Optional if {@code sourceId} does not exist.
     */
    Optional<ManualSchemaRow> duplicate(String sourceId, String newName, String createdBy);

    boolean deleteById(String id);
}
