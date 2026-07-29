-- A binary ExtensionObject cannot be replayed faithfully without its declared
-- DataType encoding. Store this on the native DATA_TYPE declaration, separately
-- from the DataType NodeId and field definitions.
alter table schema_nodes add column data_type_default_encoding_id text;

comment on column schema_nodes.data_type_default_encoding_id is
    'Server-provided OPC UA StructureDefinition default binary encoding NodeId';
