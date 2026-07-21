-- Manual schemas (IS-171): a reusable, standalone protocol-neutral structure artifact
-- (folders + typed variables, no values) — symmetric to `recordings` but for structure
-- instead of values. Not bound to any data-source; consumed only as the parameter set
-- for a synthetic source (see backend-specs/03_DOMAIN_MODEL.md §ManualSchema). Nodes are
-- stored as a single jsonb column (same SchemaNode[] shape as recordings.schema_nodes,
-- IS-161) rather than normalized schema_nodes rows — there is no versioned schema_id to
-- key normalized rows off here, and no consumer needs per-node querying.

create table manual_schemas (
    id          varchar primary key,
    project_id  varchar not null references projects (id) on delete cascade,
    protocol    varchar not null check (protocol in ('OPC_UA', 'MODBUS_TCP')),
    name        varchar not null,
    description varchar,
    nodes       jsonb not null default '[]'::jsonb,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    created_by  varchar not null default 'local',
    version     bigint not null default 0
);

create index idx_manual_schemas_project on manual_schemas (project_id);
