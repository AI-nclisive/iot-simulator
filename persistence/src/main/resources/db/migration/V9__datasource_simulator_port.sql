-- IS-127: split the overloaded data-source endpoint into a first-class
-- simulator_port (serve port) and a nullable real_device_endpoint (real device).

-- 1. New serve-port column (nullable until backfilled).
alter table data_sources add column simulator_port int;

-- 2. Backfill from runtime_config.listenPort, else per-protocol default; then
--    drop the migrated key. Postgres-only jsonb operators — skipped by jOOQ codegen.
-- [jooq ignore start]
update data_sources
   set simulator_port = coalesce(
        nullif(runtime_config->>'listenPort', '')::int,
        case protocol when 'MODBUS_TCP' then 502 else 4840 end);
update data_sources
   set runtime_config = runtime_config - 'listenPort';
-- [jooq ignore stop]

-- 3. Serve port is now always known.
alter table data_sources alter column simulator_port set not null;

-- 4. Rename endpoint -> real_device_endpoint and make it nullable.
alter table data_sources rename column endpoint to real_device_endpoint;
alter table data_sources alter column real_device_endpoint drop not null;
alter table data_sources alter column real_device_endpoint drop default;

-- 5. A real-device address is meaningless for synthetic sources — null them out.
-- [jooq ignore start]
update data_sources set real_device_endpoint = null where basis = 'SYNTHETIC';
-- [jooq ignore stop]
