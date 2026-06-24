-- Recordings, samples, scenarios, faults. See backend-specs/03 & 04.

create table recordings (
    id              varchar primary key,
    project_id      varchar not null references projects (id) on delete cascade,
    data_source_id  varchar not null references data_sources (id) on delete cascade,
    schema_version  int not null,
    origin          varchar not null check (origin in ('SCAN_RECORD', 'IMPORTED')),
    time_start      timestamptz,
    time_end        timestamptz,
    value_count     bigint not null default 0,
    size_bytes      bigint not null default 0,
    tags            jsonb not null default '[]'::jsonb,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    created_by      varchar not null default 'local',
    version         bigint not null default 0
);

create table samples (
    id                       varchar primary key,
    project_id               varchar not null references projects (id) on delete cascade,
    derived_from_recording_id varchar references recordings (id) on delete set null,
    name                     varchar not null,
    selection                jsonb not null default '{}'::jsonb,
    tags                     jsonb not null default '[]'::jsonb,
    created_at               timestamptz not null default now(),
    updated_at               timestamptz not null default now(),
    created_by               varchar not null default 'local',
    version                  bigint not null default 0
);

create table scenarios (
    id                    varchar primary key,
    project_id            varchar not null references projects (id) on delete cascade,
    name                  varchar not null,
    status                varchar not null default 'DRAFT' check (status in ('DRAFT', 'READY', 'INVALID')),
    deterministic_settings jsonb not null default '{}'::jsonb,
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now(),
    created_by            varchar not null default 'local',
    version               bigint not null default 0
);

create table scenario_steps (
    scenario_id      varchar not null references scenarios (id) on delete cascade,
    ordinal          int not null,
    type             varchar not null check (type in
                       ('START', 'STOP', 'REPLAY', 'SYNTHETIC', 'FAULT', 'WAIT', 'MARKER')),
    target_source_id varchar,
    params           jsonb not null default '{}'::jsonb,
    primary key (scenario_id, ordinal)
);

create table faults (
    id          varchar primary key,
    project_id  varchar not null references projects (id) on delete cascade,
    kind        varchar not null check (kind in
                  ('BAD_VALUE', 'MISSING_VALUE', 'DELAY', 'CONNECTION_DROP',
                   'TIMEOUT', 'PROTOCOL_ERROR', 'SOURCE_UNAVAILABLE')),
    layer       varchar not null check (layer in ('NEUTRAL', 'PROTOCOL')),
    target      jsonb not null default '{}'::jsonb,
    params      jsonb not null default '{}'::jsonb,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    created_by  varchar not null default 'local',
    version     bigint not null default 0
);
