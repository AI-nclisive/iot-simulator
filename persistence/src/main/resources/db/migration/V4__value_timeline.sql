-- Append-optimized value timeline. Captures every value change on the recording
-- path (no sampling). Time-ordered range reads for replay and evidence.
-- No TimescaleDB / no required extension. See backend-specs/04_DB_SCHEMA.md.
--
-- Native range partitioning by source_time (declarative, no extension): the
-- partition key (source_time) is part of the primary key, as Postgres requires.
-- A DEFAULT partition guarantees inserts always land somewhere; monthly
-- partitions are attached for ranges that warrant their own segment, and old
-- partitions are dropped for retention/cleanup.
--
-- jOOQ's DDLDatabase parser (POSTGRES dialect) does not understand the
-- partitioning clauses, so they are wrapped in [jooq ignore] markers: Flyway and
-- Postgres execute the full statements, while codegen sees a plain table with the
-- correct columns.
create table value_timeline (
    recording_id   varchar not null,
    node_id        varchar not null,
    source_time    timestamptz not null,
    seq            bigint not null,
    value_kind     varchar not null check (value_kind in ('NUM', 'INT', 'BOOL', 'TEXT', 'BYTES')),
    value_enc      bytea,
    quality        varchar not null default 'GOOD' check (quality in ('GOOD', 'UNCERTAIN', 'BAD')),
    quality_reason varchar,
    primary key (recording_id, node_id, source_time, seq)
) /* [jooq ignore start] */ partition by range (source_time) /* [jooq ignore stop] */;

-- Catch-all so the parent always accepts writes even before a monthly partition
-- exists. Split monthly partitions out of / attach alongside this as scale and
-- retention require; drop old partitions to reclaim space.
-- [jooq ignore start]
create table value_timeline_default partition of value_timeline default;
-- [jooq ignore stop]
