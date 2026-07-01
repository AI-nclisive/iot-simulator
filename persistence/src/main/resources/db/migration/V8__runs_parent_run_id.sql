-- Parent link for scenario runs: a SCENARIO run's REPLAY/SYNTHETIC steps open child
-- runs that point back at the parent (IS-086). See backend-specs/03 & 04.
alter table runs
    add column parent_run_id varchar references runs (id) on delete cascade;

create index runs_parent_run_id_idx on runs (parent_run_id);
