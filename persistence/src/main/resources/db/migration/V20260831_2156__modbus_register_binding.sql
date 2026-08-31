-- IS-060: optional explicit Modbus register/coil binding override per schema node.
-- Both columns are null unless a user pins an explicit address; absent means the
-- worker computes the default contiguous layout (protocol-model spec §5).
ALTER TABLE schema_nodes
    ADD COLUMN modbus_register_kind VARCHAR(32),
    ADD COLUMN modbus_address INTEGER;

ALTER TABLE schema_nodes
    ADD CONSTRAINT schema_nodes_modbus_register_kind_check
    CHECK (modbus_register_kind IS NULL
        OR modbus_register_kind IN ('COIL', 'DISCRETE_INPUT', 'HOLDING_REGISTER', 'INPUT_REGISTER'));

ALTER TABLE schema_nodes
    ADD CONSTRAINT schema_nodes_modbus_binding_pair_check
    CHECK ((modbus_register_kind IS NULL) = (modbus_address IS NULL));
