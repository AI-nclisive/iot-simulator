-- Preserve the OPC UA DataType declaration and the members of a schema-local
-- structured DataType.  Earlier IS-183 API fields were transient: saving a
-- schema silently discarded them, so a reopened schema no longer matched the
-- scanned source.
alter table schema_nodes add column data_type_node_id varchar;
alter table schema_nodes add column data_type_members jsonb not null default '[]'::jsonb;

comment on column schema_nodes.data_type_node_id is
    'Original OPC UA DataType NodeId when no neutral scalar DataType is used.';
comment on column schema_nodes.data_type_members is
    'Ordered member declarations for a schema DATA_TYPE node.';
