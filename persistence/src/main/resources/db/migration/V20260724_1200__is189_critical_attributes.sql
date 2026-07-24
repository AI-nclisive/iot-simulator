-- IS-189: Materialize Variable Property/Component children with correct reference types
-- Add critical OPC UA attributes for complete device reproduction (IEC 62541)

-- New columns for critical OPC UA attributes
ALTER TABLE schema_nodes ADD COLUMN access_level_full integer;
ALTER TABLE schema_nodes ADD COLUMN minimum_sampling_interval integer;
ALTER TABLE schema_nodes ADD COLUMN write_mask integer;
ALTER TABLE schema_nodes ADD COLUMN historizing boolean;

-- Comments for documentation
COMMENT ON COLUMN schema_nodes.access_level_full IS
  'IEC 62541 AccessLevel 8-bit mask: bits for CurrentRead(0), CurrentWrite(1), HistoryRead(2), HistoryWrite(3), SemanticChange(4), StatusWrite(5), TimestampWrite(6)';
COMMENT ON COLUMN schema_nodes.minimum_sampling_interval IS
  'Server minimum sampling interval in milliseconds (-1 = indeterminate, 0 = continuous)';
COMMENT ON COLUMN schema_nodes.write_mask IS
  'UInt32 mask: which node attributes can be written by clients (0 = all immutable)';
COMMENT ON COLUMN schema_nodes.historizing IS
  'Does server actively collect historical values for this variable?';

-- schema_node_references table already exists (IS-176)
-- IS-189: It now supports VARIABLE as target for HasProperty/HasComponent edges
COMMENT ON TABLE schema_node_references IS
  'IS-189: Typed schema references. HasProperty/HasComponent now support VARIABLE targets for Property/Component children.';
