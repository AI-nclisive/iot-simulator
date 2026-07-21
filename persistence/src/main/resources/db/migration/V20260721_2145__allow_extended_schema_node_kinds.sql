-- IS-176: retain the original migration while allowing the additive OPC UA address-space node kinds.
alter table schema_nodes drop constraint if exists schema_nodes_kind_check;
alter table schema_nodes add constraint schema_nodes_kind_check
    check (kind in ('FOLDER', 'OBJECT', 'VARIABLE', 'METHOD'));
