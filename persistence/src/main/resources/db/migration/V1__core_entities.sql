-- Core entities. See backend-specs/03_DOMAIN_MODEL.md and 04_DB_SCHEMA.md.
-- Enums stored as varchar + check constraints for portability across managed Postgres.

create table project_settings (
    id          varchar primary key,
    defaults    jsonb not null default '{}'::jsonb,
    retention   jsonb not null default '{}'::jsonb,
    metadata    jsonb not null default '{}'::jsonb
);

create table environment_settings (
    id          varchar primary key,
    deployment_mode varchar not null default 'local'
        check (deployment_mode in ('local', 'shared')),
    identity    jsonb not null default '{}'::jsonb,
    storage     jsonb not null default '{}'::jsonb,
    retention   jsonb not null default '{}'::jsonb
);

create table projects (
    id          varchar primary key,
    name        varchar not null,
    description varchar,
    status      varchar not null default 'ACTIVE' check (status in ('ACTIVE', 'ARCHIVED')),
    settings_id varchar references project_settings (id),
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    created_by  varchar not null default 'local',
    version     bigint not null default 0
);

create table schemas (
    id              varchar primary key,
    data_source_id  varchar not null,
    version         int not null,
    created_at      timestamptz not null default now(),
    unique (data_source_id, version)
);

create table data_sources (
    id              varchar primary key,
    project_id      varchar not null references projects (id) on delete cascade,
    name            varchar not null,
    protocol        varchar not null check (protocol in ('OPC_UA', 'MODBUS_TCP')),
    basis           varchar not null check (basis in ('SCAN', 'MANUAL', 'IMPORT', 'SYNTHETIC')),
    schema_id       varchar references schemas (id),
    schema_version  int,
    endpoint        jsonb not null default '{}'::jsonb,
    runtime_config  jsonb not null default '{}'::jsonb,
    enabled         boolean not null default false,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    created_by      varchar not null default 'local',
    version         bigint not null default 0
);

create table schema_nodes (
    schema_id        varchar not null references schemas (id) on delete cascade,
    node_id          varchar not null,
    parent_id        varchar,
    path             varchar not null,
    name             varchar not null,
    kind             varchar not null check (kind in ('FOLDER', 'VARIABLE')),
    data_type        varchar,
    value_rank       varchar check (value_rank in ('SCALAR', 'ARRAY')),
    access           varchar check (access in ('READ', 'READ_WRITE')),
    unit             varchar,
    description      varchar,
    protocol_bindings jsonb not null default '{}'::jsonb,
    primary key (schema_id, node_id),
    unique (schema_id, path)
);

create index idx_data_sources_project on data_sources (project_id);
