# Database Schema & Value-Timeline Design (DRAFT)

Status: **DRAFT — proposal for approval.** Postgres schema for the domain model
(`03_DOMAIN_MODEL.md`), with the append-optimized value-timeline design required
by `ARCHITECTURE.md`. Stack: PostgreSQL (no required extensions), Flyway
migrations, JDBC + jOOQ (typed SQL, no heavy ORM) — per `STACK.md`. No
TimescaleDB or any required extension.

## Conventions

- IDs: `text` ULID (sortable, app-generated). PKs `text`.
- Timestamps: `timestamptz`. Value times: `timestamptz` at microsecond precision.
- Optimistic concurrency: `version bigint not null default 0` on mutable entities.
- Enums: stored as `text` with `check` constraints (portable across managed
  Postgres; no PG enum types to keep migrations simple).
- Audit columns: `created_at`, `updated_at`, `created_by`.
- jOOQ code generation runs against the Flyway-migrated schema in build.

## Core entity tables

| Table | Key columns / notes |
| --- | --- |
| `projects` | `name`, `description`, `status`, `settings_id`, `version` |
| `data_sources` | `project_id`→projects, `protocol`, `basis`, `schema_id`, `schema_version`, `endpoint` (jsonb), `runtime_config` (jsonb), `enabled`, `version` |
| `schemas` | `data_source_id`, `version` (int), `created_at`; unique (`data_source_id`,`version`) |
| `schema_nodes` | `schema_id`, `node_id`, `parent_id`, `path`, `name`, `kind`, `data_type`, `value_rank`, `access`, `unit`, `description`, `protocol_bindings` (jsonb); unique (`schema_id`,`node_id`) |
| `manual_schemas` | `project_id`→projects, `protocol`, `name`, `description`, `nodes` (jsonb array of `SchemaNode`, same shape/precedent as `recordings.schema_nodes` — no normalized node rows), `version` |
| `recordings` | `project_id`, `data_source_id`, `schema_version`, `origin`, `time_start`, `time_end`, `value_count`, `size_bytes`, `tags` (jsonb), `version` |
| `samples` | `project_id`, `derived_from_recording_id`, `selection` (jsonb), `name`, `tags`, `version` |
| `scenarios` | `project_id`, `name`, `status`, `deterministic_settings` (jsonb), `version` |
| `scenario_steps` | `scenario_id`, `ordinal`, `type`, `target_source_id`, `params` (jsonb) |
| `faults` | `project_id`, `kind`, `layer`, `target` (jsonb), `params` (jsonb), `version` |
| `runs` | `project_id`, `kind`, `trigger`, `initiator`, `state`, `started_at`, `ended_at`, `scenario_id`, `evidence_id` |
| `run_sources` | `run_id`, `data_source_id` (join) |
| `evidence` | `run_id`, `project_id`, `status`, `manifest` (jsonb), `object_ref` (export blob in ObjectStore) |
| `client_connections` | `data_source_id`, `client_id`, `connected_at`, `disconnected_at`, `summary` (jsonb) |

## Runtime vs audit streams (separate tables)

| Table | Notes |
| --- | --- |
| `runtime_events` | `project_id`, `data_source_id`, `run_id`, `type`, `at`, `payload` (jsonb); append-only; indexed by (`project_id`,`at`) and (`run_id`,`at`) |
| `activity_events` | `project_id`, `actor`, `action`, `object_type`, `object_id`, `at`, `detail` (jsonb); append-only; **never merged** with runtime_events |

## Value timeline (append-optimized)

The hot path. Captures **every** value change on the recording path (no
sampling). Design goals: cheap appends with batching + backpressure, fast
time-ordered range reads for replay and evidence — **without** a time-series
extension.

### Table `value_timeline`

| Column | Type | Notes |
| --- | --- | --- |
| `recording_id` | text | Owning recording (or live capture session). |
| `node_id` | text | Variable node from the schema. |
| `source_time` | timestamptz(6) | Authoritative ordering key (`01_…` §3). |
| `seq` | bigint | Monotonic tiebreaker within (`recording_id`,`node_id`). |
| `value_kind` | text | Encoding tag (`NUM | INT | BOOL | TEXT | BYTES`). |
| `value_enc` | bytea | Compact encoded value (per `data_type`). |
| `quality` | text | `GOOD | UNCERTAIN | BAD`. |
| `quality_reason` | text | nullable. |

- **Primary/clustering key**: (`recording_id`, `node_id`, `source_time`, `seq`).
  Replay/evidence read ordered ranges by this key — sequential, index-only-ish.
- **Range read**: `where recording_id=? and source_time between ? and ?
  order by source_time, seq`.
- **Encoding**: single `value_enc` bytea keeps the row narrow and write-cheap;
  `value_kind` lets readers decode without a schema lookup. (Alternative —
  per-type columns — rejected for write width; revisit if analytical SQL over
  values becomes a requirement.)

### Partitioning

- **Enabled: native range partitioning by `source_time`**, declarative — no
  extension (IS-093). The partition key is part of the primary key, as Postgres
  requires. A `DEFAULT` partition guarantees appends always land somewhere even
  before a monthly partition exists; monthly partitions (granularity TBD by
  retention need) are attached as scale warrants. Old partitions support
  retention/cleanup (`frontend/docs/UI_SCREEN_SPECS.md` Retention & Cleanup) by
  dropping partitions.
- jOOQ's `DDLDatabase` parser does not understand the partitioning clauses, so
  they are wrapped in `[jooq ignore]` markers in `V4`: Flyway/Postgres execute the
  full DDL while codegen sees a plain table with the correct columns.
- Per-recording locality is preserved by the clustering key, so most replays read
  one recording within a few partitions.

### Write path

- Batched inserts (`COPY`/multi-row) from the recording pipeline with
  backpressure when the writer lags (`ARCHITECTURE.md`). Live UI path is a
  separate conflated/throttled stream, **not** written here per-change.

## Auth tables (shared mode)

| Table | Notes |
| --- | --- |
| `users` | `subject` (OIDC), `display_name`, `status`, `last_seen_at` |
| `roles` | `name` (`admin`,`user`, future-expandable) |
| `permissions` | `name` (capability), seeded |
| `role_permissions` | join (flexible model, D2) |
| `user_roles` | join |
| `edit_leases` | `object_type`, `object_id`, `holder`, `acquired_at`, `expires_at` (advisory lock for shared edit, `08_…`) |

Local trusted mode does not require these to be populated.

## Migration plan (Flyway)

- `V1__core_entities.sql` — projects, data_sources, schemas, schema_nodes, settings.
- `V2__recordings_samples_scenarios_faults.sql`
- `V3__runs_evidence_clients.sql`
- `V4__value_timeline.sql` (range-partitioned by `source_time` + `DEFAULT` partition)
- `V5__runtime_and_activity_events.sql`
- `V6__auth_and_leases.sql`

The DataSource is externally configured (env-based) so the same migrations run
against containerized Postgres + volume or a managed instance (`STACK.md`).

## Open questions for reviewer

- Confirm single encoded `value_enc` bytea vs per-type columns.
- Confirm monthly partition granularity (partitioning itself is now enabled from
  day one — IS-093).
