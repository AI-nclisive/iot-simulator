package com.ainclusive.iotsim.domain.auth;

/**
 * Fine-grained capability constants for the flexible permission model (D2).
 *
 * <p>Authorization is checked against these permissions; roles map to permission sets. The
 * current roles ({@code admin}, {@code user}) define the baseline; the model expands to more roles
 * (viewer/operator/editor) later without changing enforcement points.
 *
 * <p>Permission names are dot-scoped strings stored in the {@code permissions} DB table and seeded
 * by {@link PermissionSeeder}. Each constant's string value is what is persisted.
 *
 * <p>Baseline mapping (see backend-specs/08_AUTH_AND_MODES.md):
 * <ul>
 *   <li>{@code user} role: observe everything + operate runtime (start/stop sources and replays).
 *       Cannot edit, import/export, or manage access.
 *   <li>{@code admin} role: everything {@code user} can do plus edit, import/export, retention,
 *       and access management.
 * </ul>
 */
public enum Permission {

    // ---------- observe (read-only) ----------

    /** Read projects, data sources, schemas, recordings, runs, samples, scenarios. */
    PROJECT_READ("project.read"),

    /** Read recording evidence (exported artefacts and evidence packages). */
    EVIDENCE_READ("evidence.read"),

    // ---------- runtime operations ----------

    /** Start a data source. */
    SOURCE_START("source.start"),

    /** Stop a data source. */
    SOURCE_STOP("source.stop"),

    /** Configure (connect / apply settings to) a data source at runtime. */
    SOURCE_CONFIGURE("source.configure"),

    /** Start a replay run. */
    REPLAY_START("replay.start"),

    /** Stop a replay run. */
    REPLAY_STOP("replay.stop"),

    /** Start a scenario run. */
    SCENARIO_RUN_START("scenario.run.start"),

    /** Stop a scenario run. */
    SCENARIO_RUN_STOP("scenario.run.stop"),

    // ---------- edit (structural changes) ----------

    /** Create, update, archive, or delete projects and their metadata. */
    PROJECT_WRITE("project.write"),

    /** Create, update, or delete data sources (not start/stop — see SOURCE_*). */
    SOURCE_WRITE("source.write"),

    /** Save or delete schema versions. */
    SCHEMA_WRITE("schema.write"),

    /** Create, update, or delete recordings and samples. */
    RECORDING_WRITE("recording.write"),

    /** Create, update, or delete scenarios. */
    SCENARIO_WRITE("scenario.write"),

    // ---------- import / export ----------

    /** Import a project, recording, or data source from an external artifact. */
    ARTIFACT_IMPORT("artifact.import"),

    /** Export a project, recording, or evidence package as an artifact. */
    EVIDENCE_EXPORT("evidence.export"),

    // ---------- admin ----------

    /** All administrative actions: user management, role assignment, retention/cleanup. */
    ADMIN_ACCESS("admin.access");

    private final String key;

    Permission(String key) {
        this.key = key;
    }

    /**
     * Returns the dot-scoped string key as stored in the database.
     *
     * @return the permission key, e.g. {@code "source.start"}
     */
    public String key() {
        return key;
    }

    /**
     * Looks up a {@link Permission} by its string key.
     *
     * @param key the dot-scoped key, e.g. {@code "source.start"}
     * @return the matching constant
     * @throws IllegalArgumentException if no constant matches
     */
    public static Permission fromKey(String key) {
        for (Permission p : values()) {
            if (p.key.equals(key)) {
                return p;
            }
        }
        throw new IllegalArgumentException("Unknown permission key: " + key);
    }
}
