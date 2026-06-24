-- Shared-mode auth (flexible permission model, D2) and advisory edit leases.
-- Local trusted mode does not require these to be populated.
-- See backend-specs/08_AUTH_AND_MODES.md.

create table users (
    id           varchar primary key,
    subject      varchar not null unique, -- OIDC subject
    display_name varchar,
    status       varchar not null default 'ACTIVE',
    last_seen_at timestamptz
);

create table roles (
    name varchar primary key -- 'admin', 'user' now; expandable later
);

create table permissions (
    name varchar primary key -- fine-grained capability, e.g. source.start
);

create table role_permissions (
    role_name       varchar not null references roles (name) on delete cascade,
    permission_name varchar not null references permissions (name) on delete cascade,
    primary key (role_name, permission_name)
);

create table user_roles (
    user_id   varchar not null references users (id) on delete cascade,
    role_name varchar not null references roles (name) on delete cascade,
    primary key (user_id, role_name)
);

create table edit_leases (
    object_type varchar not null,
    object_id   varchar not null,
    holder      varchar not null,
    acquired_at timestamptz not null default now(),
    expires_at  timestamptz not null,
    primary key (object_type, object_id)
);

insert into roles (name) values ('admin'), ('user');
