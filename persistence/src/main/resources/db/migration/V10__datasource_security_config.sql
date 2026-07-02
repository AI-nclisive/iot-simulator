-- IS-131: per-source simulated OPC UA endpoint security (user-token policy + hashed
-- credentials). Empty '{}' reproduces the historical None/Anonymous server.
alter table data_sources
    add column security_config jsonb not null default '{}'::jsonb;
