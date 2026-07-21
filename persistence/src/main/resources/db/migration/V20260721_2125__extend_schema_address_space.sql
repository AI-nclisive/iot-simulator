-- IS-176: attributes and typed links needed to model an OPC UA address space.
alter table schema_nodes add column array_dimensions integer[];
alter table schema_nodes add column type_definition varchar;

create table schema_node_references (
    schema_id varchar not null,
    source_node_id varchar not null,
    target_node_id varchar not null,
    reference_type varchar not null check (reference_type in (
        'ORGANIZES', 'HAS_COMPONENT', 'HAS_PROPERTY', 'HAS_TYPE_DEFINITION', 'GENERIC')),
    is_forward boolean not null default true,
    primary key (schema_id, source_node_id, target_node_id, reference_type, is_forward),
    foreign key (schema_id, source_node_id) references schema_nodes (schema_id, node_id) on delete cascade,
    foreign key (schema_id, target_node_id) references schema_nodes (schema_id, node_id) on delete cascade
);
