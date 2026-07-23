-- Value timeline for a run that has no backing Recording (SYNTHETIC/SCENARIO runs
-- generate values live and never persist them to `value_timeline`, which is keyed by
-- recording_id and only written by Recording capture / read by Replay). Evidence
-- export for such runs otherwise always ships an empty value-timeline.json — see
-- backend-specs/TASKS.md IS-185.
--
-- Same shape as value_timeline (04_DB_SCHEMA.md) but keyed by run_id instead of
-- recording_id, and not partitioned — run-scoped volumes are bounded by run duration,
-- unlike a long-lived recording. Rows are deleted alongside the run's evidence/value
-- data by ordinary run retention; add partitioning later if volume warrants it.
create table run_value_timeline (
    run_id         varchar not null,
    node_id        varchar not null,
    source_time    timestamptz not null,
    seq            bigint not null,
    value_kind     varchar not null check (value_kind in ('NUM', 'INT', 'BOOL', 'TEXT', 'BYTES')),
    value_enc      bytea,
    quality        varchar not null default 'GOOD' check (quality in ('GOOD', 'UNCERTAIN', 'BAD')),
    quality_reason varchar,
    primary key (run_id, node_id, source_time, seq)
);
