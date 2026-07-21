package com.ainclusive.iotsim.protocolmodel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates address-space topology before a schema is persisted or projected. */
public final class SchemaNodeValidator {
    private SchemaNodeValidator() {}

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
        }
        for (SchemaNode node : nodes) {
            assertAcyclic(node, byId, new HashSet<>());
        }
    }

    private static void validateParent(SchemaNode node, Map<String, SchemaNode> byId) {
        if (node.parentId() == null) {
            return;
        }
        SchemaNode parent = byId.get(node.parentId());
        if (parent == null) {
            throw new IllegalArgumentException("parent does not exist: " + node.parentId());
        }
        if (parent.kind() != NodeKind.FOLDER && parent.kind() != NodeKind.OBJECT) {
            throw new IllegalArgumentException("parent must be a FOLDER or OBJECT: " + parent.nodeId());
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
