package com.ainclusive.iotsim.protocolmodel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates address-space topology before a schema is persisted or projected. */
public final class SchemaNodeValidator {
    private SchemaNodeValidator() {}

    /**
     * Detects variables whose declared native type cannot be captured/replayed
     * (IS-199: opaque/vendor types) — an OPAQUE declaration the source never
     * supplied a definition for, or a structure/union with no default binary
     * encoding. Returns one diagnostic per affected variable rather than
     * throwing, so a caller can report every issue at once (schema save or run
     * start), not just the first.
     */
    public static List<String> validateTypes(List<SchemaNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        Map<String, NativeTypeDefinition> catalog = new HashMap<>();
        for (NativeTypeDefinition definition : NativeTypeCatalog.fromSchemaNodes(nodes)) {
            catalog.put(definition.typeId(), definition);
        }
        List<String> issues = new ArrayList<>();
        for (SchemaNode node : nodes) {
            if (node.kind() != NodeKind.VARIABLE || node.dataTypeNodeId() == null) {
                continue;
            }
            NativeTypeDefinition definition = catalog.get(node.dataTypeNodeId());
            if (definition == null) {
                continue; // a standard OPC UA DataType NodeId, not a schema-native declaration
            }
            NativeTypeCapability capability = definition.capability();
            if (capability.materializable() && capability.captureDecodable() && capability.replayEncodable()) {
                continue;
            }
            issues.add("variable '" + node.name() + "' (" + node.nodeId() + ") uses unsupported native type '"
                    + definition.browseName() + "' (" + definition.kind() + "): " + capability.unavailableReason()
                    + " — " + recoveryHint(definition.kind()));
        }
        return issues;
    }

    private static String recoveryHint(NativeTypeKind kind) {
        return switch (kind) {
            case OPAQUE -> "re-scan the source so its native type definition is captured, "
                    + "or change this variable to a supported type";
            case STRUCTURE, UNION -> "supply a default binary encoding for this type, "
                    + "or change this variable to a supported type";
            case ENUM, OPTION_SET -> "change this variable to a supported type";
        };
    }

    public static void validate(List<SchemaNode> nodes) {
        if (nodes == null) {
            throw new IllegalArgumentException("nodes are required");
        }
        Map<String, SchemaNode> byId = new HashMap<>();
        Set<String> paths = new HashSet<>();
        for (SchemaNode node : nodes) {
            if (byId.putIfAbsent(node.nodeId(), node) != null) {
                throw new IllegalArgumentException("duplicate nodeId: " + node.nodeId());
            }
            if (!paths.add(node.path())) {
                throw new IllegalArgumentException("duplicate node path: " + node.path());
            }
        }
        for (SchemaNode node : nodes) {
            validateParent(node, byId);
            for (SchemaReference reference : node.references()) {
                if (!byId.containsKey(reference.targetNodeId())) {
                    throw new IllegalArgumentException("reference target does not exist: " + reference.targetNodeId());
                }
            }
            validateDataTypeNodeId(node, byId);
            validateMembers(node, byId);
            validateEnumValues(node);
        }
        assertAcyclicDataTypes(byId);
        for (SchemaNode node : nodes) {
            assertAcyclic(node, byId, new HashSet<>());
        }
    }

    /** A VARIABLE can reference a schema DATA_TYPE or a preserved standard OPC UA type NodeId. */
    private static void validateDataTypeNodeId(SchemaNode node, Map<String, SchemaNode> byId) {
        if (node.kind() != NodeKind.VARIABLE || node.dataTypeNodeId() == null) {
            return;
        }
        SchemaNode target = byId.get(node.dataTypeNodeId());
        if (target == null) {
            if (node.dataTypeNodeId().matches("(?:ns=\\d+;)?[isgb]=.+")) {
                return;
            }
            throw new IllegalArgumentException("dataTypeNodeId target does not exist: " + node.dataTypeNodeId());
        }
        if (target.kind() != NodeKind.DATA_TYPE) {
            throw new IllegalArgumentException(
                    "dataTypeNodeId target must be a DATA_TYPE node: " + node.dataTypeNodeId());
        }
    }

    /** A DATA_TYPE's members must have unique names and valid local/native type references. */
    private static void validateMembers(SchemaNode node, Map<String, SchemaNode> byId) {
        if (node.kind() != NodeKind.DATA_TYPE) {
            return;
        }
        Set<String> memberNames = new HashSet<>();
        for (DataTypeMember member : node.members()) {
            if (!memberNames.add(member.name())) {
                throw new IllegalArgumentException(
                        "duplicate member name '" + member.name() + "' in DATA_TYPE node: " + node.nodeId());
            }
            if (member.dataTypeNodeId() == null) {
                continue;
            }
            if (member.dataTypeNodeId().equals(node.nodeId())) {
                throw new IllegalArgumentException(
                        "DATA_TYPE node '" + node.nodeId() + "' cannot reference itself in member '"
                                + member.name() + "'");
            }
            SchemaNode target = byId.get(member.dataTypeNodeId());
            if (target == null) {
                if (member.dataTypeNodeId().matches("(?:ns=\\d+;)?[isgb]=.+")) {
                    continue; // a standard or vendor-native declaration, preserved verbatim
                }
                throw new IllegalArgumentException(
                        "member '" + member.name() + "' dataTypeNodeId does not exist: "
                                + member.dataTypeNodeId());
            }
            if (target.kind() != NodeKind.DATA_TYPE) {
                throw new IllegalArgumentException(
                        "member '" + member.name() + "' dataTypeNodeId must be a DATA_TYPE node: "
                                + member.dataTypeNodeId());
            }
        }
    }

    private static void assertAcyclicDataTypes(Map<String, SchemaNode> byId) {
        for (SchemaNode node : byId.values()) {
            if (node.kind() == NodeKind.DATA_TYPE) {
                assertDataTypeAcyclic(node.nodeId(), byId, new HashSet<>(), new HashSet<>());
            }
        }
    }

    private static void validateEnumValues(SchemaNode node) {
        if (node.kind() != NodeKind.DATA_TYPE || node.enumValues().isEmpty()) {
            return;
        }
        Set<String> names = new HashSet<>();
        Set<Long> values = new HashSet<>();
        for (DataTypeEnumValue value : node.enumValues()) {
            if (!names.add(value.name())) {
                throw new IllegalArgumentException(
                        "duplicate enum value name '" + value.name() + "' in DATA_TYPE node: " + node.nodeId());
            }
            if (!values.add(value.value())) {
                throw new IllegalArgumentException(
                        "duplicate enum numeric value " + value.value() + " in DATA_TYPE node: " + node.nodeId());
            }
        }
    }

    private static void assertDataTypeAcyclic(String id, Map<String, SchemaNode> byId,
            Set<String> visiting, Set<String> complete) {
        if (complete.contains(id)) {
            return;
        }
        if (!visiting.add(id)) {
            throw new IllegalArgumentException("cyclic DATA_TYPE member reference at: " + id);
        }
        SchemaNode type = byId.get(id);
        for (DataTypeMember member : type.members()) {
            if (member.dataTypeNodeId() != null && byId.containsKey(member.dataTypeNodeId())) {
                assertDataTypeAcyclic(member.dataTypeNodeId(), byId, visiting, complete);
            }
        }
        visiting.remove(id);
        complete.add(id);
    }

    private static void validateParent(SchemaNode node, Map<String, SchemaNode> byId) {
        if (node.parentId() == null) {
            return;
        }
        SchemaNode parent = byId.get(node.parentId());
        if (parent == null) {
            throw new IllegalArgumentException("parent does not exist: " + node.parentId());
        }
        // IS-189: Allow VARIABLE as parent for Property/Component edges (HasProperty/HasComponent references)
        if (parent.kind() != NodeKind.FOLDER && parent.kind() != NodeKind.OBJECT && parent.kind() != NodeKind.VARIABLE) {
            throw new IllegalArgumentException("parent must be a FOLDER, OBJECT, or VARIABLE: " + parent.nodeId());
        }
    }

    private static void assertAcyclic(SchemaNode node, Map<String, SchemaNode> byId, Set<String> visiting) {
        if (node.parentId() == null) {
            return;
        }
        if (!visiting.add(node.nodeId())) {
            throw new IllegalArgumentException("cyclic parent relationship at: " + node.nodeId());
        }
        SchemaNode parent = byId.get(node.parentId());
        if (parent != null) {
            assertAcyclic(parent, byId, visiting);
        }
        visiting.remove(node.nodeId());
    }
}
