-- Back the per-source runtime-event query (RuntimeEventRepository.findByDataSource)
-- with an index, matching the existing (project_id, at) and (run_id, at) indexes
-- from V5. Without it, filtering by data_source_id would full-scan at volume.
-- See backend-specs/04 & IS-049.

create index idx_runtime_events_data_source on runtime_events (data_source_id, at);
