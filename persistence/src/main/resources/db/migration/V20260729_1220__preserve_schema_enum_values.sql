-- Enum and option-set literals are part of a native DataType declaration, not
-- variable values. Keep their names and exact numeric values with the schema.
alter table schema_nodes add column data_type_enum_values jsonb not null default '[]'::jsonb;

comment on column schema_nodes.data_type_enum_values is
    'Declared numeric literals for an OPC UA enum or option-set DATA_TYPE node.';
