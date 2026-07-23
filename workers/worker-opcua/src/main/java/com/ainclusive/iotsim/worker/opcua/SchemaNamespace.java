package com.ainclusive.iotsim.worker.opcua;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.DataItem;
import org.eclipse.milo.opcua.sdk.server.api.ManagedNamespaceWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.api.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;

/**
 * Builds the OPC UA address space from the protocol-neutral schema: each VARIABLE
 * becomes a {@link UaVariableNode} under the Objects folder, and ApplyValues
 * updates node values. See backend-specs/01 §5 and 02.
 */
final class SchemaNamespace extends ManagedNamespaceWithLifecycle {

    static final String URI = "urn:iotsim:opcua";

    private final List<VarDef> variables;
    private final Map<String, UaVariableNode> nodes = new ConcurrentHashMap<>();
    private final Map<String, org.eclipse.milo.opcua.stack.core.types.builtin.NodeId> hierarchy = new ConcurrentHashMap<>();
    private final SubscriptionModel subscriptionModel;

    SchemaNamespace(OpcUaServer server, List<VarDef> variables) {
        super(server, URI);
        this.variables = List.copyOf(variables);
        this.subscriptionModel = new SubscriptionModel(server, this);
        getLifecycleManager().addLifecycle(subscriptionModel);
        getLifecycleManager().addStartupTask(this::createNodes);
    }

    @Override
    public void onDataItemsCreated(List<DataItem> dataItems) {
        subscriptionModel.onDataItemsCreated(dataItems);
    }

    @Override
    public void onDataItemsModified(List<DataItem> dataItems) {
        subscriptionModel.onDataItemsModified(dataItems);
    }

    @Override
    public void onDataItemsDeleted(List<DataItem> dataItems) {
        subscriptionModel.onDataItemsDeleted(dataItems);
    }

    @Override
    public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems) {
        subscriptionModel.onMonitoringModeChanged(monitoredItems);
    }

    private void createNodes() {
        // Folders are built first, repeatedly, so an out-of-order schema still
        // materializes correctly. A missing parent is rejected rather than flattened.
        int remaining = (int) variables.stream()
                .filter(def -> "FOLDER".equals(def.kind()) || "OBJECT".equals(def.kind())).count();
        while (remaining > 0) {
            int created = 0;
            for (VarDef def : variables) {
                if (!("FOLDER".equals(def.kind()) || "OBJECT".equals(def.kind()))
                        || hierarchy.containsKey(def.nodeId())
                        || (def.parentId() != null && !hierarchy.containsKey(def.parentId()))) {
                    continue;
                }
                var nodeId = newNodeId(def.nodeId());
                UaObjectNode node = UaObjectNode.builder(getNodeContext())
                        .setNodeId(nodeId)
                        .setBrowseName(newQualifiedName(def.name()))
                        .setDisplayName(LocalizedText.english(def.name()))
                        .setTypeDefinition("FOLDER".equals(def.kind())
                                ? Identifiers.FolderType
                                : Identifiers.BaseObjectType)
                        .build();
                getNodeManager().addNode(node);
                var parent = def.parentId() == null ? Identifiers.ObjectsFolder : hierarchy.get(def.parentId());
                node.addReference(new Reference(nodeId, Identifiers.Organizes, parent.expanded(), false));
                hierarchy.put(def.nodeId(), nodeId);
                created++;
            }
            if (created == 0) {
                throw new IllegalArgumentException("Schema contains an object or folder with a missing or cyclic parent");
            }
            remaining -= created;
        }
        for (VarDef def : variables) {
            if (!"VARIABLE".equals(def.kind())) {
                continue;
            }
            boolean parentIsFolder = def.parentId() == null || hierarchy.containsKey(def.parentId());
            // Test-only (VarDef.referenceType): a HasProperty fixture's parent is another
            // Variable, not a Folder/Object — a real device's own address space allows this,
            // even though the neutral schema model never produces it (SchemaNodeValidator).
            boolean parentIsVariable = !parentIsFolder && nodes.containsKey(def.parentId());
            if (!parentIsFolder && !parentIsVariable) {
                throw new IllegalArgumentException("Variable has a missing or non-folder parent: " + def.nodeId());
            }
            UaVariableNode node = UaVariableNode.builder(getNodeContext())
                    .setNodeId(newNodeId(def.nodeId()))
                    .setAccessLevel(AccessLevel.CurrentRead, AccessLevel.CurrentWrite)
                    .setBrowseName(newQualifiedName(def.name()))
                    .setDisplayName(LocalizedText.english(def.name()))
                    .setDataType(OpcUaTypes.dataTypeId(def.dataType()))
                    .setTypeDefinition(Identifiers.BaseDataVariableType)
                    .build();
            node.setValue(new DataValue(new Variant(OpcUaTypes.defaultValue(def.dataType()))));
            getNodeManager().addNode(node);
            var parent = def.parentId() == null ? Identifiers.ObjectsFolder
                    : parentIsFolder ? hierarchy.get(def.parentId()) : nodes.get(def.parentId()).getNodeId();
            var referenceType = def.referenceType() == null ? Identifiers.Organizes : referenceTypeId(def.referenceType());
            node.addReference(new Reference(node.getNodeId(), referenceType, parent.expanded(), false));
            nodes.put(def.nodeId(), node);
        }
    }

    /** Test-only seam (VarDef.referenceType): resolves a reference type name to its NodeId. */
    private static org.eclipse.milo.opcua.stack.core.types.builtin.NodeId referenceTypeId(String name) {
        return switch (name) {
            case "HAS_PROPERTY" -> Identifiers.HasProperty;
            case "HAS_COMPONENT" -> Identifiers.HasComponent;
            default -> throw new IllegalArgumentException("unknown referenceType: " + name);
        };
    }

    void updateValue(String nodeId, Object opcUaValue) {
        UaVariableNode node = nodes.get(nodeId);
        if (node != null) {
            node.setValue(new DataValue(new Variant(opcUaValue)));
        }
    }
}
