ALTER TABLE data_sources
    ADD COLUMN real_device_unit_id INTEGER;

ALTER TABLE data_sources
    ADD CONSTRAINT data_sources_real_device_unit_id_range_check
    CHECK (real_device_unit_id IS NULL OR real_device_unit_id BETWEEN 0 AND 255);

-- IS-213 encoded the selected unit in a JSON-string endpoint as `#<unitId>`.
-- Split only syntactically valid Modbus suffixes; malformed historical values
-- remain untouched rather than being silently reinterpreted.
-- [jooq ignore start]
UPDATE data_sources
SET real_device_unit_id = ((regexp_match(real_device_endpoint #>> '{}', '#([0-9]{1,3})$'))[1])::INTEGER,
    real_device_endpoint = to_jsonb(regexp_replace(real_device_endpoint #>> '{}', '#[0-9]{1,3}$', ''))
WHERE protocol = 'MODBUS_TCP'
  AND real_device_endpoint #>> '{}' ~ '#([0-9]{1,3})$'
  AND ((regexp_match(real_device_endpoint #>> '{}', '#([0-9]{1,3})$'))[1])::INTEGER BETWEEN 0 AND 255;

UPDATE data_sources
SET real_device_unit_id = 1
WHERE protocol = 'MODBUS_TCP'
  AND real_device_endpoint IS NOT NULL
  AND real_device_unit_id IS NULL
  AND real_device_endpoint #>> '{}' NOT LIKE '%#%';
-- [jooq ignore stop]
