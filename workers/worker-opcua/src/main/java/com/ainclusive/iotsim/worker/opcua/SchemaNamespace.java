package com.ainclusive.iotsim.worker.opcua;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.core.types.DynamicOptionSetType;
import org.eclipse.milo.opcua.sdk.core.types.DynamicStructType;
import org.eclipse.milo.opcua.sdk.core.types.DynamicUnionType;
import org.eclipse.milo.opcua.sdk.core.types.codec.DynamicCodecFactory;
import org.eclipse.milo.opcua.sdk.core.typetree.DataType;
import org.eclipse.milo.opcua.sdk.server.ManagedNamespaceWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.items.DataItem;
import org.eclipse.milo.opcua.sdk.server.items.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.nodes.UaDataTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.UaStructuredType;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.StructureType;
import org.eclipse.milo.opcua.stack.core.types.structured.DataTypeDefinition;
import org.eclipse.milo.opcua.stack.core.types.structured.EnumDefinition;
import org.eclipse.milo.opcua.stack.core.types.structured.EnumField;
import org.eclipse.milo.opcua.stack.core.types.structured.EnumValueType;
import org.eclipse.milo.opcua.stack.core.types.structured.StructureDefinition;
import org.eclipse.milo.opcua.stack.core.types.structured.StructureField;

/**
 * Builds the OPC UA address space from the protocol-neutral schema: each VARIABLE
 * becomes a {@link UaVariableNode} under the Objects folder, and ApplyValues
 * updates node values. See backend-specs/01 §5 and 02.
 */
final class SchemaNamespace extends ManagedNamespaceWithLifecycle {

    static final String URI = "urn:iotsim:opcua";

    private final List<VarDef> variables;
    private final List<NativeDataTypeDef> typeDefinitions;
    private final Map<String, UaVariableNode> nodes = new ConcurrentHashMap<>();
    private final Map<String, org.eclipse.milo.opcua.stack.core.types.builtin.NodeId> hierarchy = new ConcurrentHashMap<>();
    private final Map<String, org.eclipse.milo.opcua.stack.core.types.builtin.NodeId> nativeDataTypes =
            new ConcurrentHashMap<>();
    private final Map<String, UaDataTypeNode> nativeDataTypeNodes = new ConcurrentHashMap<>();
    private final Map<String, DataType> executableStructures = new ConcurrentHashMap<>();
    private final Map<String, DataType> executableUnions = new ConcurrentHashMap<>();
    private final Map<String, DataType> executableOptionSets = new ConcurrentHashMap<>();
    /** Local encoding identities used only by this server's address space. */
    private final Map<String, org.eclipse.milo.opcua.stack.core.types.builtin.NodeId> nativeDataTypeEncodings =
            new ConcurrentHashMap<>();
    private final SubscriptionModel subscriptionModel;

    SchemaNamespace(OpcUaServer server, List<VarDef> variables) {
        this(server, variables, List.of());
    }

    SchemaNamespace(OpcUaServer server, List<VarDef> variables, List<NativeDataTypeDef> typeDefinitions) {
        super(server, URI);
        this.variables = List.copyOf(variables);
        this.typeDefinitions = List.copyOf(typeDefinitions);
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
        createNativeDataTypes();
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
            // IS-189: A HasProperty/HasComponent child's parent is another Variable
            boolean parentIsVariable = !parentIsFolder && nodes.containsKey(def.parentId());
            if (!parentIsFolder && !parentIsVariable) {
                throw new IllegalArgumentException("Variable has a missing or non-folder/variable parent: " + def.nodeId());
            }
            UaVariableNode node = UaVariableNode.builder(getNodeContext())
                    .setNodeId(newNodeId(def.nodeId()))
                    .setAccessLevel(AccessLevel.CurrentRead, AccessLevel.CurrentWrite)
                    .setBrowseName(newQualifiedName(def.name()))
                    .setDisplayName(LocalizedText.english(def.name()))
                    .setDataType(declaredDataType(def))
                    .setTypeDefinition(Identifiers.BaseDataVariableType)
                    .build();
            if (!isStandardOpcUaDataType(def.dataTypeNodeId()) && def.dataType() != null && !def.dataType().isBlank()) {
                node.setValue(new DataValue(new Variant(OpcUaTypes.defaultValue(def.dataType()))));
            }
            getNodeManager().addNode(node);
            var parent = def.parentId() == null ? Identifiers.ObjectsFolder
                    : parentIsFolder ? hierarchy.get(def.parentId()) : nodes.get(def.parentId()).getNodeId();
            var referenceType = def.referenceType() == null ? Identifiers.Organizes : referenceTypeId(def.referenceType());
            node.addReference(new Reference(node.getNodeId(), referenceType, parent.expanded(), false));
            nodes.put(def.nodeId(), node);
        }
        for (VarDef def : variables) {
            if (!"METHOD".equals(def.kind())) {
                continue;
            }
            boolean parentIsFolder = def.parentId() == null || hierarchy.containsKey(def.parentId());
            boolean parentIsVariable = !parentIsFolder && nodes.containsKey(def.parentId());
            if (!parentIsFolder && !parentIsVariable) {
                throw new IllegalArgumentException("Method has a missing or unsupported parent: " + def.nodeId());
            }
            var nodeId = newNodeId(def.nodeId());
            UaMethodNode method = new UaMethodNode(
                    getNodeContext(), nodeId, newQualifiedName(def.name()), LocalizedText.english(def.name()),
                    LocalizedText.english(def.name()),
                    null, null, true, true);
            // A scanned/manual schema describes the presence of a method but does not define
            // executable business logic. Expose it truthfully and make invocation fail with
            // the OPC UA standard Bad_NotImplemented response instead of silently omitting it.
            method.setInvocationHandler(org.eclipse.milo.opcua.sdk.server.methods.MethodInvocationHandler.NOT_IMPLEMENTED);
            getNodeManager().addNode(method);
            var parent = def.parentId() == null ? Identifiers.ObjectsFolder
                    : parentIsFolder ? hierarchy.get(def.parentId()) : nodes.get(def.parentId()).getNodeId();
            method.addReference(new Reference(nodeId, Identifiers.HasComponent, parent.expanded(), false));
        }
    }

    private void createNativeDataTypes() {
        for (NativeDataTypeDef definition : typeDefinitions) {
            var nodeId = newNodeId("types/" + definition.nodeId());
            UaDataTypeNode node = new UaDataTypeNode(
                    getNodeContext(),
                    nodeId,
                    newQualifiedName(definition.name()),
                    LocalizedText.english(definition.name()),
                    LocalizedText.english(definition.name()),
                    null,
                    null,
                    false);
            if (definition.isEnum()) {
                node.setEnumValues(definition.enumValues().stream()
                        .map(value -> new EnumValueType(
                                value.getValue(),
                                LocalizedText.english(value.getName()),
                                value.getDescription().isBlank() ? null : LocalizedText.english(value.getDescription())))
                        .toArray(EnumValueType[]::new));
            }
            if (definition.isOptionSet()) {
                node.setOptionSetValues(definition.enumValues().stream()
                        .map(value -> LocalizedText.english(value.getName()))
                        .toArray(LocalizedText[]::new));
                node.setDataTypeDefinition(new EnumDefinition(definition.enumValues().stream()
                        .map(value -> new EnumField(
                                value.getValue(),
                                LocalizedText.english(value.getName()),
                                value.getDescription().isBlank()
                                        ? null : LocalizedText.english(value.getDescription()),
                                value.getName()))
                        .toArray(EnumField[]::new)));
            }
            getNodeManager().addNode(node);
            var baseType = definition.isEnum() ? Identifiers.Enumeration
                    : definition.isOptionSet() ? Identifiers.OptionSet : Identifiers.Structure;
            node.addReference(new Reference(nodeId, Identifiers.HasSubtype, baseType.expanded(), false));
            nativeDataTypes.put(definition.nodeId(), nodeId);
            nativeDataTypeNodes.put(definition.nodeId(), node);
        }
        for (NativeDataTypeDef definition : typeDefinitions) {
            UaDataTypeNode node = nativeDataTypeNodes.get(definition.nodeId());
            boolean needsEncoding = (definition.isStructure() && definition.hasDefaultEncoding())
                    || (definition.isOptionSet() && !definition.enumValues().isEmpty());
            if (needsEncoding) {
                var encodingId = newNodeId("encodings/" + definition.nodeId() + "/DefaultBinary");
                UaObjectNode encoding = UaObjectNode.builder(getNodeContext())
                        .setNodeId(encodingId)
                        .setBrowseName(newQualifiedName("Default Binary"))
                        .setDisplayName(LocalizedText.english("Default Binary"))
                        .setTypeDefinition(Identifiers.DataTypeEncodingType)
                        .build();
                getNodeManager().addNode(encoding);
                node.addReference(new Reference(
                        node.getNodeId(), Identifiers.HasEncoding, encodingId.expanded(), true));
                nativeDataTypeEncodings.put(definition.nodeId(), encodingId);
                if (definition.isStructure()) {
                    node.setDataTypeDefinition(new StructureDefinition(
                            encodingId,
                            Identifiers.Structure,
                            structureType(definition),
                            definition.members().stream().map(this::structureField).toArray(StructureField[]::new)));
                }
            }
        }
    }

    private StructureField structureField(com.ainclusive.iotsim.workercontract.v1.DataTypeMemberMsg member) {
        UInteger[] dimensions = member.getArrayDimensionsList().stream()
                .map(value -> uint(value.longValue()))
                .toArray(UInteger[]::new);
        return new StructureField(
                member.getName(),
                null,
                memberDataType(member),
                "ARRAY".equals(member.getValueRank()) ? 1 : -1,
                dimensions.length == 0 ? null : dimensions,
                null,
                member.getOptional());
    }

    private org.eclipse.milo.opcua.stack.core.types.builtin.NodeId memberDataType(
            com.ainclusive.iotsim.workercontract.v1.DataTypeMemberMsg member) {
        if (!member.getDataTypeNodeId().isBlank()) {
            var local = nativeDataTypes.get(member.getDataTypeNodeId());
            return local != null
                    ? local
                    : org.eclipse.milo.opcua.stack.core.types.builtin.NodeId.parse(member.getDataTypeNodeId());
        }
        return OpcUaTypes.dataTypeId(member.getDataType());
    }

    private static StructureType structureType(NativeDataTypeDef definition) {
        if ("UNION".equals(definition.nativeTypeKind())) {
            return StructureType.Union;
        }
        return definition.members().stream().anyMatch(member -> member.getOptional())
                ? StructureType.StructureWithOptionalFields
                : StructureType.Structure;
    }

    org.eclipse.milo.opcua.stack.core.types.builtin.NodeId localEncodingId(String sourceTypeId) {
        return nativeDataTypeEncodings.get(sourceTypeId);
    }

    org.eclipse.milo.opcua.stack.core.types.builtin.NodeId localDataTypeId(String sourceTypeId) {
        return nativeDataTypes.get(sourceTypeId);
    }

    /** Registers Milo's schema-driven binary codec after the address space is live. */
    void materializeStructureCodecs(OpcUaServer server) {
        var typeTree = server.updateDataTypeTree();
        for (NativeDataTypeDef definition : typeDefinitions) {
            if ((!definition.isStructure() || !definition.hasDefaultEncoding())
                    && (!definition.isOptionSet() || definition.enumValues().isEmpty())) {
                continue;
            }
            var typeId = nativeDataTypes.get(definition.nodeId());
            var encodingId = nativeDataTypeEncodings.get(definition.nodeId());
            DataType dataType = typeId == null ? null : typeTree.getDataType(typeId);
            if (dataType == null || encodingId == null) {
                continue;
            }
            DataType executableType = new ExecutableStructureType(dataType, encodingId);
            server.getDynamicDataTypeManager().registerType(
                    typeId, DynamicCodecFactory.create(executableType, typeTree), encodingId, null, null);
            if ("UNION".equals(definition.nativeTypeKind())) {
                executableUnions.put(definition.nodeId(), executableType);
            } else if (definition.isOptionSet()) {
                executableOptionSets.put(definition.nodeId(), executableType);
            } else {
                executableStructures.put(definition.nodeId(), executableType);
            }
        }
    }

    DynamicStructType structureValue(String sourceTypeId, Map<String, Object> members) {
        DataType dataType = executableStructures.get(sourceTypeId);
        if (dataType == null) {
            throw new IllegalArgumentException("native structure has no executable runtime codec: " + sourceTypeId);
        }
        return new DynamicStructType(dataType, new java.util.LinkedHashMap<>(members));
    }

    DynamicUnionType unionValue(String sourceTypeId, String fieldName, Object fieldValue) {
        DataType dataType = executableUnions.get(sourceTypeId);
        if (dataType == null) {
            throw new IllegalArgumentException("native union has no executable runtime codec: " + sourceTypeId);
        }
        return new DynamicUnionType(dataType, new DynamicUnionType.UnionValue(fieldName, fieldValue));
    }

    DynamicOptionSetType optionSetValue(String sourceTypeId, byte[] value, byte[] validBits) {
        DataType dataType = executableOptionSets.get(sourceTypeId);
        if (dataType == null) {
            throw new IllegalArgumentException("native option set has no executable runtime codec: " + sourceTypeId);
        }
        return new DynamicOptionSetType(dataType, ByteString.of(value), ByteString.of(validBits));
    }

    /** Supplies the schema-local Default Binary encoding even when Milo's type tree has not indexed it yet. */
    private record ExecutableStructureType(DataType delegate,
            org.eclipse.milo.opcua.stack.core.types.builtin.NodeId binaryEncodingId) implements DataType {
        @Override
        public QualifiedName getBrowseName() {
            return delegate.getBrowseName();
        }

        @Override
        public org.eclipse.milo.opcua.stack.core.types.builtin.NodeId getNodeId() {
            return delegate.getNodeId();
        }

        @Override
        public org.eclipse.milo.opcua.stack.core.types.builtin.NodeId getBinaryEncodingId() {
            return binaryEncodingId;
        }

        @Override
        public org.eclipse.milo.opcua.stack.core.types.builtin.NodeId getXmlEncodingId() {
            return delegate.getXmlEncodingId();
        }

        @Override
        public org.eclipse.milo.opcua.stack.core.types.builtin.NodeId getJsonEncodingId() {
            return delegate.getJsonEncodingId();
        }

        @Override
        public DataTypeDefinition getDataTypeDefinition() {
            return delegate.getDataTypeDefinition();
        }

        @Override
        public Boolean isAbstract() {
            return delegate.isAbstract();
        }
    }

    private org.eclipse.milo.opcua.stack.core.types.builtin.NodeId declaredDataType(VarDef def) {
        if (isStandardOpcUaDataType(def.declaredDataTypeNodeId())) {
            return org.eclipse.milo.opcua.stack.core.types.builtin.NodeId.parse(def.declaredDataTypeNodeId());
        }
        if (isStandardOpcUaDataType(def.dataTypeNodeId())) {
            return org.eclipse.milo.opcua.stack.core.types.builtin.NodeId.parse(def.dataTypeNodeId());
        }
        if (def.dataTypeNodeId() != null && !def.dataTypeNodeId().isBlank()) {
            var nativeType = nativeDataTypes.get(def.dataTypeNodeId());
            if (nativeType != null) {
                return nativeType;
            }
            throw new IllegalArgumentException(
                    "cannot simulate non-standard OPC UA DataType because its declaration was not supplied: "
                            + def.dataTypeNodeId());
        }
        return OpcUaTypes.dataTypeId(def.dataType());
    }

    private static boolean isStandardOpcUaDataType(String nodeId) {
        return nodeId != null && nodeId.startsWith("ns=0;");
    }

    /** IS-189: Resolves a reference type name to its OPC UA NodeId. */
    private static org.eclipse.milo.opcua.stack.core.types.builtin.NodeId referenceTypeId(String name) {
        if (name == null) {
            return Identifiers.Organizes;  // default
        }
        return switch (name) {
            case "ORGANIZES" -> Identifiers.Organizes;
            case "HAS_PROPERTY" -> Identifiers.HasProperty;
            case "HAS_COMPONENT" -> Identifiers.HasComponent;
            case "HAS_TYPE_DEFINITION" -> Identifiers.HasTypeDefinition;
            case "GENERIC" -> Identifiers.References;  // generic reference type
            default -> throw new IllegalArgumentException("unknown referenceType: " + name);
        };
    }

    void updateValue(String nodeId, Object opcUaValue) {
        UaVariableNode node = nodes.get(nodeId);
        if (node != null) {
            Object value = opcUaValue;
            if (value instanceof UaStructuredType structure) {
                try {
                    value = ExtensionObject.encode(getServer().getDynamicEncodingContext(), structure);
                } catch (Exception e) {
                    throw new IllegalArgumentException("failed to encode native structure for " + nodeId, e);
                }
            }
            node.setValue(new DataValue(new Variant(value)));
        }
    }
}
