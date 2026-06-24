-- Runs, evidence, client connections. See backend-specs/03 & 04.

create table evidence (
    id          varchar primary key,
    project_id  varchar not null references projects (id) on delete cascade,
    run_id      varchar,
    status      varchar not null default 'CAPTURING'
                  check (status in ('CAPTURING', 'READY', 'PARTIAL', 'EXPORT_FAILED')),
    manifest    jsonb not null default '{}'::jsonb,
    object_ref  varchar, -- export blob in ObjectStore
    created_at  timestamptz not null default now(),
    created_by  varchar not null default 'local'
);

create table runs (
    id          varchar primary key,
    project_id  varchar not null references projects (id) on delete cascade,
    kind        varchar not null check (kind in ('REPLAY', 'SYNTHETIC', 'SCENARIO', 'RECORDING')),
    trigger     varchar not null default 'MANUAL' check (trigger in ('MANUAL', 'AUTOMATED')),
    initiator   varchar not null default 'local',
    state       varchar not null default 'QUEUED'
                  check (state in ('QUEUED', 'RUNNING', 'STOPPED', 'FAILED', 'COMPLETED')),
    scenario_id varchar references scenarios (id) on delete set null,
    evidence_id varchar references evidence (id) on delete set null,
    started_at  timestamptz,
    ended_at    timestamptz,
    created_at  timestamptz not null default now()
);

alter table evidence
    add constraint fk_evidence_run foreign key (run_id) references runs (id) on delete set null;

create table run_sources (
    run_id         varchar not null references runs (id) on delete cascade,
    data_source_id varchar not null references data_sources (id) on delete cascade,
    primary key (run_id, data_source_id)
);

create table client_connections (
    id              varchar primary key,
    data_source_id  varchar not null references data_sources (id) on delete cascade,
    client_id       varchar not null,
    connected_at    timestamptz not null default now(),
    disconnected_at timestamptz,
    summary         jsonb not null default '{}'::jsonb
);

create index idx_runs_project on runs (project_id, created_at);
create index idx_clients_source on client_connections (data_source_id, connected_at);
