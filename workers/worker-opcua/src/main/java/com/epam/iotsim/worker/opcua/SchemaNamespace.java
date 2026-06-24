package com.epam.iotsim.worker.opcua;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.DataItem;
import org.eclipse.milo.opcua.sdk.server.api.ManagedNamespaceWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.api.MonitoredItem;
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
        for (VarDef def : variables) {
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
            node.addReference(new Reference(
                    node.getNodeId(), Identifiers.Organizes, Identifiers.ObjectsFolder.expanded(), true));
            nodes.put(def.nodeId(), node);
        }
    }

    void updateValue(String nodeId, Object opcUaValue) {
        UaVariableNode node = nodes.get(nodeId);
        if (node != null) {
            node.setValue(new DataValue(new Variant(opcUaValue)));
        }
    }
}
