-- Recordings are scoped to a protocol type, not to the data source instance
-- they were captured from (IS-160). Replay/import binds to any compatible
-- data source of that type at run time, never at capture/import time.

alter table recordings add column protocol varchar
    check (protocol in ('OPC_UA', 'MODBUS_TCP'));

update recordings r
    set protocol = (select ds.protocol from data_sources ds where ds.id = r.data_source_id)
    where r.protocol is null
      and r.data_source_id is not null;

alter table recordings alter column protocol set not null;

alter table recordings alter column data_source_id drop not null;
