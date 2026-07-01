package com.ainclusive.iotsim.api.security;

/**
 * Fine-grained API-layer capabilities checked at every enforcement point (IS-077).
 *
 * <p>Role→permission mapping (per backend-specs/08_AUTH_AND_MODES.md, §Authorization):
 * <ul>
 *   <li>{@code user}: {@link #SOURCE_START}, {@link #SOURCE_STOP}, {@link #REPLAY_START},
 *       {@link #REPLAY_STOP}, {@link #OBSERVE} — observe everything and operate the runtime.
 *   <li>{@code admin}: all {@code user} permissions plus {@link #PROJECT_EDIT},
 *       {@link #SOURCE_EDIT}, {@link #SCHEMA_EDIT}, {@link #SCENARIO_EDIT},
 *       {@link #IMPORT_EXPORT}, {@link #ADMIN_ACCESS} — full control including
 *       edits, import/export, and access management.
 * </ul>
 *
 * <p>In local mode the implicit principal holds all permissions (IS-078);
 * in shared mode the {@link PermissionService} resolves them from the JWT claims (IS-076).
 *
 * <p>The permission names match the {@code permissions} table seed in V6 migration
 * (IS-079): {@code source.start}, {@code source.stop}, {@code replay.start},
 * {@code replay.stop}, {@code observe}, {@code project.edit}, {@code source.edit},
 * {@code schema.edit}, {@code scenario.edit}, {@code import.export}, {@code admin.access}.
 */
public enum Permission {

    /** Start a data source (runtime control — user-level). */
    SOURCE_START("source.start"),

    /** Stop a data source (runtime control — user-level). */
    SOURCE_STOP("source.stop"),

    /** Start a replay or synthetic run (runtime control — user-level). */
    REPLAY_START("replay.start"),

    /** Stop a replay or synthetic run (runtime control — user-level). */
    REPLAY_STOP("replay.stop"),

    /** Observe: read projects, data sources, schemas, recordings, live values, events (user-level). */
    OBSERVE("observe"),

    /** Create / update / delete projects (admin-level). */
    PROJECT_EDIT("project.edit"),

    /** Create / update / delete / duplicate data sources (admin-level). */
    SOURCE_EDIT("source.edit"),

    /** Create / update / delete schema versions; attach credentials (admin-level). */
    SCHEMA_EDIT("schema.edit"),

    /** Create / update / delete scenarios and steps (admin-level). */
    SCENARIO_EDIT("scenario.edit"),

    /** Import / export projects, recordings, samples, evidence (admin-level). */
    IMPORT_EXPORT("import.export"),

    /** Access user management and system settings (admin-only). */
    ADMIN_ACCESS("admin.access");

    private final String permissionName;

    Permission(String permissionName) {
        this.permissionName = permissionName;
    }

    /**
     * Returns the canonical dot-notation name as seeded in the {@code permissions} table.
     */
    public String permissionName() {
        return permissionName;
    }
}
