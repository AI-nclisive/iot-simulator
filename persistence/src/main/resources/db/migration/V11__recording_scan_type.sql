-- IS-138: add scan_type to recordings so the engine can skip value-timeline writes
-- for schema-only captures.

alter table recordings
    add column scan_type varchar not null default 'SCHEMA_AND_DATA'
        check (scan_type in ('SCHEMA_ONLY', 'SCHEMA_AND_DATA'));
