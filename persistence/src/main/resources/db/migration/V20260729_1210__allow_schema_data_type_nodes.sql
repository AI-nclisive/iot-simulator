-- DATA_TYPE is a first-class schema node kind. The original IS-183 API/model
-- accepted it, but the database check constraint omitted it and rejected every
-- persisted structured declaration (for example standard Range).
alter table schema_nodes drop constraint if exists schema_nodes_kind_check;
alter table schema_nodes add constraint schema_nodes_kind_check
    check (kind in ('FOLDER', 'OBJECT', 'VARIABLE', 'METHOD', 'DATA_TYPE'));
