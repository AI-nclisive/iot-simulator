-- Append-optimized value timeline. Captures every value change on the recording
-- path (no sampling). Time-ordered range reads for replay and evidence.
-- No TimescaleDB / no required extension. See backend-specs/04_DB_SCHEMA.md.

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
);

-- Partition-ready: this table can be converted to native range partitioning by
-- source_time (monthly) without application changes when scale requires it.
-- Old partitions then support retention/cleanup by dropping partitions.
