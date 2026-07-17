-- A recording's schema is now captured and stored WITH the recording, not fetched
-- live from the schemas/schema_nodes tables via data_source_id (IS-161). IS-160 made
-- dataSourceId nullable / non-authoritative for replay, which exposed a gap: the
-- schema-serving path (RecordingService.getRecordingSchema, RecordingProfiler) still
-- did a live lookup keyed on dataSourceId, which 404s once the data source is gone.
--
-- schema_nodes holds the recording's own SchemaNode[] snapshot (same shape as the
-- SchemaNode record: nodeId, parentId, path, name, kind, dataType, valueRank, access,
-- unit, description), independent of any data source or the live schemas table.

alter table recordings add column schema_nodes jsonb not null default '[]'::jsonb;

-- Backfill: for recordings whose (data_source_id, schema_version) still resolves to a
-- live schema, populate the snapshot from schema_nodes via a correlated subquery.
-- Recordings where it can't resolve (e.g. deleted data source) keep the default empty
-- array — matches the current "No schema captured" experience for that unrecoverable case.
update recordings r
    set schema_nodes = coalesce(
        (select jsonb_agg(jsonb_build_object(
                'nodeId', sn.node_id,
                'parentId', sn.parent_id,
                'path', sn.path,
                'name', sn.name,
                'kind', sn.kind,
                'dataType', sn.data_type,
                'valueRank', sn.value_rank,
                'access', sn.access,
                'unit', sn.unit,
                'description', sn.description
            ) order by sn.path)
         from schemas s
         join schema_nodes sn on sn.schema_id = s.id
         where s.data_source_id = r.data_source_id and s.version = r.schema_version),
        '[]'::jsonb)
    where r.data_source_id is not null;
