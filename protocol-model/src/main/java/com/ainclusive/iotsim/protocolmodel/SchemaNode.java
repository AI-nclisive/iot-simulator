package com.ainclusive.iotsim.protocolmodel;

import java.util.List;
import java.util.Objects;

/**
 * One addressable node in a protocol-neutral schema tree.
 *
 * <p>{@code nodeId} is the only stable reference (used by recordings, samples,
 * scenarios, faults, evidence). {@code path} is unique within a schema. See
 * {@code openspec/specs/protocol-model/spec.md} §1.
 *
 * @param dataType  executable protocol-neutral value type; may be accompanied by
 *                  {@code declaredDataTypeNodeId} when a scan must preserve its original OPC UA declaration
 * @param parentId  {@code null} for a root child; always {@code null} for {@link NodeKind#DATA_TYPE}
 *                  (IS-183) — a DATA_TYPE is a top-level type definition, not part of the
 *                  FOLDER/OBJECT parent-child hierarchy
 * @param typeDefinition  free-form OPC UA HasTypeDefinition target (e.g. a built-in VariableType
 *                        NodeId string parsed from NodeSet XML); orthogonal to {@code dataType}/
 *                        {@code dataTypeNodeId} and unrelated to IS-183
 * @param dataTypeNodeId  for a {@link NodeKind#VARIABLE}, either the {@code nodeId} of a custom
 *                        {@link NodeKind#DATA_TYPE} node or a preserved standard OPC UA DataType
 *                        NodeId (for example {@code ns=0;i=28} for {@code UInteger}), used instead
 *                        of a primitive {@code dataType}; {@code null} for other kinds. It preserves
 *                        a declaration whose meaning cannot be represented by the neutral enum.
 * @param members   ordered, named+typed members of a {@link NodeKind#DATA_TYPE} node's structure
 *                  (IS-183); empty for every other kind
 * @param accessLevelFull  IEC 62541 AccessLevel 8-bit mask (nullable): bits for CurrentRead(0),
 *                         CurrentWrite(1), HistoryRead(2), HistoryWrite(3), SemanticChange(4),
 *                         StatusWrite(5), TimestampWrite(6); {@code null} = server does not expose
 * @param minimumSamplingInterval  server's minimum sampling interval in milliseconds (nullable):
 *                                  -1 = indeterminate, 0 = continuous; {@code null} = unknown
 * @param writeMask  OPC UA WriteMask as Java Integer (0-2147483647): each bit (0-31) represents
 *                   a writable attribute per IEC 62541; 0 = all immutable, bits set = those attributes writable;
 *                   {@code null} = not specified
 * @param historizing  whether server actively collects historical values (nullable);
 *                     {@code null} = not specified, false = no history collection
 * @param declaredDataTypeNodeId  original OPC UA DataType NodeId declared by the source variable.
 *                                This is descriptive fidelity metadata and may accompany {@code dataType};
 *                                it is distinct from {@code dataTypeNodeId}, which selects a schema-native type.
 */
public record SchemaNode(
        String nodeId,
        String parentId,
        String path,
        String name,
        NodeKind kind,
        DataType dataType,
        ValueRank valueRank,
        Access access,
        String unit,
        String description,
        List<Integer> arrayDimensions,
        String typeDefinition,
        List<SchemaReference> references,
        String dataTypeNodeId,
        List<DataTypeMember> members,
        List<DataTypeEnumValue> enumValues,
        String defaultEncodingId,
        NativeTypeKind nativeTypeKind,
        Integer accessLevelFull,
        Integer minimumSamplingInterval,
        Integer writeMask,
        Boolean historizing,
        String declaredDataTypeNodeId,
        String modbusRegisterKind,
        Integer modbusAddress,
        String modbusByteOrder,
        String modbusWordOrder,
        Double modbusScale) {

    public SchemaNode {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        arrayDimensions = arrayDimensions == null ? List.of() : List.copyOf(arrayDimensions);
        references = references == null ? List.of() : List.copyOf(references);
        members = members == null ? List.of() : List.copyOf(members);
        enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
        if (kind == NodeKind.VARIABLE) {
            if ((dataType == null) == (dataTypeNodeId == null)) {
                throw new IllegalArgumentException(
                        "a VARIABLE node requires exactly one of dataType or dataTypeNodeId");
            }
            Objects.requireNonNull(valueRank, "valueRank is required for a VARIABLE node");
            Objects.requireNonNull(access, "access is required for a VARIABLE node");
            if (valueRank == ValueRank.SCALAR && !arrayDimensions.isEmpty()) {
                throw new IllegalArgumentException("arrayDimensions require ARRAY valueRank");
            }
            if (arrayDimensions.stream().anyMatch(dimension -> dimension < 0)) {
                throw new IllegalArgumentException("arrayDimensions must be non-negative");
            }
            // IS-189: Validate critical OPC UA attributes if present (per IEC 62541)
            if (accessLevelFull != null && (accessLevelFull < 0 || accessLevelFull > 255)) {
                throw new IllegalArgumentException("accessLevelFull must be 0-255 (8-bit): " + accessLevelFull);
            }
            // IS-060: an explicit Modbus register-map override is optional, but when present
            // both halves must be given together — a worker cannot honor a bare address without
            // knowing which object type it addresses, or vice versa.
            if ((modbusRegisterKind == null) != (modbusAddress == null)) {
                throw new IllegalArgumentException(
                        "modbusRegisterKind and modbusAddress must be set together or not at all");
            }
            if (modbusAddress != null && modbusAddress < 0) {
                throw new IllegalArgumentException("modbusAddress must be non-negative: " + modbusAddress);
            }
            if ((modbusByteOrder != null || modbusWordOrder != null || modbusScale != null)
                    && (modbusRegisterKind == null
                            || "COIL".equals(modbusRegisterKind)
                            || "DISCRETE_INPUT".equals(modbusRegisterKind))) {
                throw new IllegalArgumentException("Modbus encoding requires a modbus register binding");
            }
            if (modbusByteOrder != null && !List.of("BIG_ENDIAN", "LITTLE_ENDIAN").contains(modbusByteOrder)) {
                throw new IllegalArgumentException("invalid modbusByteOrder: " + modbusByteOrder);
            }
            if (modbusWordOrder != null && !List.of("MSW_FIRST", "LSW_FIRST").contains(modbusWordOrder)) {
                throw new IllegalArgumentException("invalid modbusWordOrder: " + modbusWordOrder);
            }
            if (modbusScale != null && (!Double.isFinite(modbusScale) || modbusScale == 0.0d)) {
                throw new IllegalArgumentException("modbusScale must be finite and non-zero");
            }
            if (writeMask != null && writeMask < 0) {
                throw new IllegalArgumentException("writeMask must be non-negative (UInt32): " + writeMask);
            }
        } else if (!arrayDimensions.isEmpty()) {
            throw new IllegalArgumentException(kind + " nodes cannot have array dimensions");
        }
        if (kind != NodeKind.VARIABLE && dataTypeNodeId != null) {
            throw new IllegalArgumentException(kind + " nodes cannot have a dataTypeNodeId");
        }
        if (kind != NodeKind.VARIABLE && (modbusRegisterKind != null || modbusByteOrder != null
                || modbusWordOrder != null || modbusScale != null)) {
            throw new IllegalArgumentException(kind + " nodes cannot have a modbusRegisterKind");
        }
        if (kind == NodeKind.DATA_TYPE) {
            nativeTypeKind = nativeTypeKind == null
                    ? !enumValues.isEmpty() ? NativeTypeKind.ENUM
                    : members.isEmpty() ? NativeTypeKind.OPAQUE : NativeTypeKind.STRUCTURE
                    : nativeTypeKind;
            if (parentId != null) {
                throw new IllegalArgumentException("DATA_TYPE nodes must be top-level (parentId must be null)");
            }
            if (!members.isEmpty() && !enumValues.isEmpty()) {
                throw new IllegalArgumentException("DATA_TYPE node '" + nodeId
                        + "' cannot mix structured members and enum values");
            }
            if (dataType != null) {
                throw new IllegalArgumentException("DATA_TYPE nodes cannot have a dataType field");
            }
            if (defaultEncodingId != null && members.isEmpty()) {
                throw new IllegalArgumentException(
                        "DATA_TYPE node '" + nodeId + "' may have a default encoding only for a structure");
            }
            if ((nativeTypeKind == NativeTypeKind.STRUCTURE || nativeTypeKind == NativeTypeKind.UNION)
                    && members.isEmpty()) {
                throw new IllegalArgumentException(nativeTypeKind + " DATA_TYPE node '" + nodeId
                        + "' requires members");
            }
            if ((nativeTypeKind == NativeTypeKind.ENUM || nativeTypeKind == NativeTypeKind.OPTION_SET)
                    && enumValues.isEmpty()) {
                throw new IllegalArgumentException(nativeTypeKind + " DATA_TYPE node '" + nodeId
                        + "' requires enum values");
            }
        } else if (!members.isEmpty() || !enumValues.isEmpty() || defaultEncodingId != null) {
            throw new IllegalArgumentException(kind + " nodes cannot have members, enum values, or a default encoding");
        } else if (nativeTypeKind != null) {
            throw new IllegalArgumentException(kind + " nodes cannot have a nativeTypeKind");
        }
    }

    /** Compatibility constructor for bindings authored before encoding metadata. */
    public SchemaNode(String nodeId, String parentId, String path, String name, NodeKind kind,
            DataType dataType, ValueRank valueRank, Access access, String unit, String description,
            List<Integer> arrayDimensions, String typeDefinition, List<SchemaReference> references,
            String dataTypeNodeId, List<DataTypeMember> members, List<DataTypeEnumValue> enumValues,
            String defaultEncodingId, NativeTypeKind nativeTypeKind, Integer accessLevelFull,
            Integer minimumSamplingInterval, Integer writeMask, Boolean historizing,
            String declaredDataTypeNodeId, String modbusRegisterKind, Integer modbusAddress) {
        this(nodeId, parentId, path, name, kind, dataType, valueRank, access, unit, description,
                arrayDimensions, typeDefinition, references, dataTypeNodeId, members, enumValues,
                defaultEncodingId, nativeTypeKind, accessLevelFull, minimumSamplingInterval, writeMask,
                historizing, declaredDataTypeNodeId, modbusRegisterKind, modbusAddress, null, null, null);
    }

    /** Compatibility constructor for schemas stored before declared OPC UA type metadata. */
    public SchemaNode(String nodeId, String parentId, String path, String name, NodeKind kind,
            DataType dataType, ValueRank valueRank, Access access, String unit, String description,
            List<Integer> arrayDimensions, String typeDefinition, List<SchemaReference> references,
            String dataTypeNodeId, List<DataTypeMember> members, List<DataTypeEnumValue> enumValues,
            String defaultEncodingId, NativeTypeKind nativeTypeKind, Integer accessLevelFull,
            Integer minimumSamplingInterval, Integer writeMask, Boolean historizing) {
        this(nodeId, parentId, path, name, kind, dataType, valueRank, access, unit, description,
                arrayDimensions, typeDefinition, references, dataTypeNodeId, members, enumValues,
                defaultEncodingId, nativeTypeKind, accessLevelFull, minimumSamplingInterval, writeMask,
                historizing, null, null, null);
    }

    /** Compatibility constructor for schemas stored before the native type-kind catalog metadata. */
    public SchemaNode(String nodeId, String parentId, String path, String name, NodeKind kind,
            DataType dataType, ValueRank valueRank, Access access, String unit, String description,
            List<Integer> arrayDimensions, String typeDefinition, List<SchemaReference> references,
            String dataTypeNodeId, List<DataTypeMember> members, List<DataTypeEnumValue> enumValues,
            String defaultEncodingId, Integer accessLevelFull, Integer minimumSamplingInterval,
            Integer writeMask, Boolean historizing) {
        this(nodeId, parentId, path, name, kind, dataType, valueRank, access, unit, description,
                arrayDimensions, typeDefinition, references, dataTypeNodeId, members, enumValues,
                defaultEncodingId, null, accessLevelFull, minimumSamplingInterval, writeMask, historizing, null, null, null);
    }

    /** Backward-compatible constructor for OPC-UA address-space nodes authored before IS-189 (critical attributes). */
    public SchemaNode(String nodeId, String parentId, String path, String name, NodeKind kind,
            DataType dataType, ValueRank valueRank, Access access, String unit, String description,
            List<Integer> arrayDimensions, String typeDefinition, List<SchemaReference> references) {
        this(nodeId, parentId, path, name, kind, dataType, valueRank, access, unit, description,
                arrayDimensions, typeDefinition, references, null, List.of(),
                List.of(), null, null, null, null, null, null, null, null, null);  // IS-189 fields = null
    }

    /** Compatibility constructor for callers that do not declare enum values. */
    public SchemaNode(String nodeId, String parentId, String path, String name, NodeKind kind,
            DataType dataType, ValueRank valueRank, Access access, String unit, String description,
            List<Integer> arrayDimensions, String typeDefinition, List<SchemaReference> references,
            String dataTypeNodeId, List<DataTypeMember> members, Integer accessLevelFull,
            Integer minimumSamplingInterval, Integer writeMask, Boolean historizing) {
        this(nodeId, parentId, path, name, kind, dataType, valueRank, access, unit, description,
                arrayDimensions, typeDefinition, references, dataTypeNodeId, members, List.of(),
                null, null, accessLevelFull, minimumSamplingInterval, writeMask, historizing, null, null, null);
    }

    /** Compatibility constructor for callers that do not declare a structure encoding. */
    public SchemaNode(String nodeId, String parentId, String path, String name, NodeKind kind,
            DataType dataType, ValueRank valueRank, Access access, String unit, String description,
            List<Integer> arrayDimensions, String typeDefinition, List<SchemaReference> references,
            String dataTypeNodeId, List<DataTypeMember> members, List<DataTypeEnumValue> enumValues,
            Integer accessLevelFull, Integer minimumSamplingInterval, Integer writeMask, Boolean historizing) {
        this(nodeId, parentId, path, name, kind, dataType, valueRank, access, unit, description,
                arrayDimensions, typeDefinition, references, dataTypeNodeId, members, enumValues,
                null, null, accessLevelFull, minimumSamplingInterval, writeMask, historizing, null, null, null);
    }

    /** Backward-compatible constructor for folders and scalar/array variables authored before IS-176. */
    public SchemaNode(String nodeId, String parentId, String path, String name, NodeKind kind,
            DataType dataType, ValueRank valueRank, Access access, String unit, String description) {
        this(nodeId, parentId, path, name, kind, dataType, valueRank, access, unit, description,
                List.of(), null, List.of(), null, List.of(),
                List.of(), null, null, null, null, null, null, null, null, null);  // IS-183 + IS-189 fields = null
    }
}
