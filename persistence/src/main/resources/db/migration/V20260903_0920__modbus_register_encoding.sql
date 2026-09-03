-- IS-220: optional vendor-specific register encoding, defaults remain null/identity.
ALTER TABLE schema_nodes
    ADD COLUMN modbus_byte_order VARCHAR(16),
    ADD COLUMN modbus_word_order VARCHAR(16),
    ADD COLUMN modbus_scale DOUBLE PRECISION;

ALTER TABLE schema_nodes
    ADD CONSTRAINT schema_nodes_modbus_byte_order_check
        CHECK (modbus_byte_order IS NULL OR modbus_byte_order IN ('BIG_ENDIAN', 'LITTLE_ENDIAN')),
    ADD CONSTRAINT schema_nodes_modbus_word_order_check
        CHECK (modbus_word_order IS NULL OR modbus_word_order IN ('MSW_FIRST', 'LSW_FIRST')),
    ADD CONSTRAINT schema_nodes_modbus_scale_check
        CHECK (modbus_scale IS NULL OR modbus_scale <> 0);
