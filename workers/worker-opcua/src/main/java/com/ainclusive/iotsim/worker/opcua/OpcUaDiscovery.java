package com.ainclusive.iotsim.worker.opcua;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import com.ainclusive.iotsim.workercontract.v1.DataTypeEnumValueMsg;
import com.ainclusive.iotsim.workercontract.v1.DataTypeMemberMsg;
import com.ainclusive.iotsim.workercontract.v1.NativeDataTypeDefinitionMsg;
import com.ainclusive.iotsim.workercontract.v1.SchemaNodeMsg;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.nodes.UaDataTypeNode;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.NamespaceTable;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseResultMask;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult;
import org.eclipse.milo.opcua.stack.core.types.structured.EnumValueType;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadResponse;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.StructureDefinition;
import org.eclipse.milo.opcua.stack.core.types.structured.StructureField;

/**
 * OPC UA <em>client-mode</em> discovery for create-from-scan (IS-043). Connects to
 * a real server, optionally authenticates, and browses its address space into
 * protocol-neutral schema nodes. Stateless and one-shot — invoked via the
 * {@code Scan}/{@code TestConnection} worker RPCs; nothing here touches the
 * embedded server runtime. See backend-specs/02 §6 and 01 §5.
 *
 * <p>Folders/objects become {@code FOLDER} nodes; variables become {@code VARIABLE}
 * nodes whose neutral type is reverse-mapped from the OPC UA built-in type (an
 * unmapped type is left empty = "unknown" for the user to resolve). The standard
 * {@code Server} subtree is skipped so a scan reflects the source's own content.
 */
final class OpcUaDiscovery {

    static final String OK = OpcUaClientSupport.OK;
    static final String PARTIAL = "PARTIAL";
    static final String UNREACHABLE = OpcUaClientSupport.UNREACHABLE;
    static final String AUTH_FAILURE = OpcUaClientSupport.AUTH_FAILURE;

    private static final int DEFAULT_MAX_NODES = 5_000;
    private static final long READ_TIMEOUT_SECONDS = 10;

    private OpcUaDiscovery() {}

    /** Session-only credentials passed from the supervisor; never persisted/logged. */
    record Credentials(String mode, String username, String secret) {}

    record ConnectionTest(String status, String message) {}

    record ScanOutcome(String status, List<SchemaNodeMsg> nodes, boolean truncated,
            int unknownCount, String message) {}

    /** IS-189: Attributes read from a Variable node (nullable fields = not available from server).
     *  minimumSamplingInterval is a Double per OPC UA §5.6.2 (server minimum sampling interval in milliseconds).
     */
    record VariableAttributes(Integer accessLevel, Double minimumSamplingInterval,
            Boolean historizing, Integer writeMask, Integer userAccessLevel, Integer valueRank) {}

    /** Reachability/auth probe: connect then disconnect, classifying any failure. */
    static ConnectionTest testConnection(String endpointUrl, Credentials credentials) {
        OpcUaClient client = null;
        try {
            client = connect(endpointUrl, credentials);
            return new ConnectionTest(OK, "connection succeeded");
        } catch (Exception e) {
            OpcUaClientSupport.reinterruptIfNeeded(e);
            return new ConnectionTest(OpcUaClientSupport.classify(e), OpcUaClientSupport.rootMessage(e));
        } finally {
            OpcUaClientSupport.disconnectQuietly(client);
        }
    }

    /** Reports discovered-so-far while a scan's browse is in progress; called from the browsing thread. */
    @FunctionalInterface
    interface ProgressListener {
        void onDiscovered(int soFar);
    }

    /** Nodes discovered between progress callbacks — frequent enough to feel live, rare enough not to flood the RPC. */
    private static final int PROGRESS_STEP = 20;

    /** IS-189: Maps OPC UA ReferenceTypeId to our ReferenceType enum values. */
    private static String mapReferenceType(NodeId refTypeId) {
        if (refTypeId == null) {
            return "ORGANIZES";
        }
        if (Identifiers.Organizes.equals(refTypeId)) {
            return "ORGANIZES";
        }
        if (Identifiers.HasProperty.equals(refTypeId)) {
            return "HAS_PROPERTY";
        }
        if (Identifiers.HasComponent.equals(refTypeId)) {
            return "HAS_COMPONENT";
        }
        if (Identifiers.HasTypeDefinition.equals(refTypeId)) {
            return "HAS_TYPE_DEFINITION";
        }
        return "GENERIC";
    }

    /** IS-189: Batch-read critical OPC UA attributes for a Variable node. */
    private static VariableAttributes readVariableAttributes(OpcUaClient client, NodeId nodeId) {
        try {
            List<ReadValueId> toRead = List.of(
                    new ReadValueId(nodeId, AttributeId.AccessLevel.uid(), null, QualifiedName.NULL_VALUE),
                    new ReadValueId(nodeId, AttributeId.MinimumSamplingInterval.uid(), null, QualifiedName.NULL_VALUE),
                    new ReadValueId(nodeId, AttributeId.Historizing.uid(), null, QualifiedName.NULL_VALUE),
                    new ReadValueId(nodeId, AttributeId.WriteMask.uid(), null, QualifiedName.NULL_VALUE),
                    new ReadValueId(nodeId, AttributeId.UserAccessLevel.uid(), null, QualifiedName.NULL_VALUE),
                    new ReadValueId(nodeId, AttributeId.ValueRank.uid(), null, QualifiedName.NULL_VALUE)
            );

            ReadResponse response = client.read(0.0, TimestampsToReturn.Neither, toRead)
                    .get(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            DataValue[] results = response.getResults();

            if (results == null || results.length < 6) {
                return new VariableAttributes(null, null, null, null, null, null);
            }

            Integer accessLevel = extractIntValue(results[0]);
            Double minimumSamplingInterval = extractDoubleValue(results[1]);
            Boolean historizing = extractBoolValue(results[2]);
            Integer writeMask = extractIntValue(results[3]);
            Integer userAccessLevel = extractIntValue(results[4]);
            Integer valueRank = extractIntValue(results[5]);

            return new VariableAttributes(accessLevel, minimumSamplingInterval, historizing, writeMask,
                    userAccessLevel, valueRank);
        } catch (Exception e) {
            return new VariableAttributes(null, null, null, null, null, null);
        }
    }

    private static Integer extractIntValue(DataValue dv) {
        if (dv == null || dv.getValue() == null) {
            return null;
        }
        Object val = dv.getValue().getValue();
        if (val instanceof Number n) {
            return n.intValue();
        }
        return null;
    }

    /** Extract Double value from DataValue, preserving decimal precision for MinimumSamplingInterval. */
    private static Double extractDoubleValue(DataValue dv) {
        if (dv == null || dv.getValue() == null) {
            return null;
        }
        Object val = dv.getValue().getValue();
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        return null;
    }

    private static Boolean extractBoolValue(DataValue dv) {
        if (dv == null || dv.getValue() == null) {
            return null;
        }
        Object val = dv.getValue().getValue();
        if (val instanceof Boolean b) {
            return b;
        }
        return null;
    }

    /** Connects and browses the address space into neutral schema nodes. */
    static ScanOutcome scan(String endpointUrl, Credentials credentials, int maxNodes,
            Runnable onConnected, ProgressListener onProgress) {
        int cap = maxNodes > 0 ? maxNodes : DEFAULT_MAX_NODES;
        OpcUaClient client = null;
        try {
            client = connect(endpointUrl, credentials);
            onConnected.run();
            return browse(client, cap, onProgress);
        } catch (Exception e) {
            OpcUaClientSupport.reinterruptIfNeeded(e);
            return new ScanOutcome(
                    OpcUaClientSupport.classify(e), List.of(), false, 0, OpcUaClientSupport.rootMessage(e));
        } finally {
            OpcUaClientSupport.disconnectQuietly(client);
        }
    }

    private static OpcUaClient connect(String endpointUrl, Credentials credentials) throws Exception {
        return OpcUaClientSupport.connect(
                endpointUrl,
                credentials == null ? "ANONYMOUS" : credentials.mode(),
                credentials == null ? null : credentials.username(),
                credentials == null ? null : credentials.secret());
    }

    private static ScanOutcome browse(OpcUaClient client, int cap, ProgressListener onProgress) throws Exception {
        NamespaceTable namespaces = client.getNamespaceTable();
        List<SchemaNodeMsg> nodes = new ArrayList<>();
        Set<NodeId> visited = new HashSet<>();
        int[] unknown = {0};
        boolean truncated;

        // Iterative DFS so a deep address space cannot blow the stack.
        Deque<Frame> stack = new ArrayDeque<>();
        stack.push(new Frame(Identifiers.ObjectsFolder, null, ""));
        outer:
        while (!stack.isEmpty()) {
            Frame frame = stack.pop();
            for (ReferenceDescription ref : browseChildren(client, frame.nodeId())) {
                NodeId childId = ref.getNodeId().toNodeId(namespaces).orElse(null);
                if (childId == null || childId.equals(Identifiers.Server) || !visited.add(childId)) {
                    continue;
                }
                boolean isVariable = ref.getNodeClass() == NodeClass.Variable;
                if (!isVariable && ref.getNodeClass() != NodeClass.Object) {
                    continue;
                }

                // IS-189: No longer skip Property/Component nodes — now supported via VARIABLE parents
                // Capture reference type for all nodes (used for HasProperty/HasComponent edges)
                String referenceType = mapReferenceType(ref.getReferenceTypeId());

                if (nodes.size() >= cap) {
                    truncated = true;
                    return new ScanOutcome(PARTIAL, List.copyOf(nodes), truncated, unknown[0],
                            "stopped at " + cap + " discovered nodes");
                }
                String name = displayName(ref);
                String neutralId = childId.toParseableString();
                String path = frame.path().isEmpty() ? name : frame.path() + "/" + name;

                // IS-189: Read critical attributes for Variables
                VariableAttributes attrs = null;
                if (isVariable) {
                    attrs = readVariableAttributes(client, childId);
                }

                DataTypeDeclaration declaration = isVariable
                        ? dataTypeOf(client, childId, unknown) : null;
                nodes.add(toNode(neutralId, frame.parentId(), path, name, isVariable,
                        declaration, referenceType, attrs));

                // A Variable can itself have children (e.g. a structured/complex
                // value's component Variables via HasComponent), not just
                // Objects/Folders — keep descending.
                stack.push(new Frame(childId, neutralId, path));
                if (nodes.size() % PROGRESS_STEP == 0) {
                    onProgress.onDiscovered(nodes.size());
                }
            }
        }
        truncated = false;
        int unknownCount = unknown[0];
        String message = unknownCount == 0
                ? "discovered " + nodes.size() + " nodes"
                : "discovered " + nodes.size() + " nodes; " + unknownCount + " of unknown type";
        return new ScanOutcome(OK, List.copyOf(nodes), truncated, unknownCount, message);
    }

    private record Frame(NodeId nodeId, String parentId, String path) {}

    private static List<ReferenceDescription> browseChildren(OpcUaClient client, NodeId nodeId)
            throws Exception {
        BrowseDescription browse = new BrowseDescription(
                nodeId,
                BrowseDirection.Forward,
                Identifiers.HierarchicalReferences,
                true,
                uint(NodeClass.Object.getValue() | NodeClass.Variable.getValue()),
                uint(BrowseResultMask.All.getValue()));
        BrowseResult result = client.browse(browse).get(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        List<ReferenceDescription> all = new ArrayList<>();
        addRefs(all, result.getReferences());
        // Servers cap references per node (maxReferencesPerNode); follow continuation
        // points so a folder with many children isn't silently truncated.
        ByteString continuation = result.getContinuationPoint();
        while (continuation != null && continuation.isNotNull()) {
            BrowseResult next = client.browseNext(false, continuation)
                    .get(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            addRefs(all, next.getReferences());
            continuation = next.getContinuationPoint();
        }
        return all;
    }

    private static void addRefs(List<ReferenceDescription> target, ReferenceDescription[] refs) {
        if (refs != null) {
            target.addAll(List.of(refs));
        }
    }

    /** The neutral mapping plus the original DataType attribute, when it needs preserving. */
    private record DataTypeDeclaration(String neutralType, String dataTypeNodeId,
            List<DataTypeMemberMsg> members, List<DataTypeEnumValueMsg> enumValues,
            String defaultEncodingId, List<NativeDataTypeDefinitionMsg> dependencies) {}

    /** The fields and binary encoding supplied by a native StructureDefinition. */
    private record StructureDeclaration(List<DataTypeMemberMsg> members, String defaultEncodingId) {
        private static final StructureDeclaration EMPTY = new StructureDeclaration(List.of(), null);
    }

    /** Reads a variable's DataType attribute and reverse-maps it; counts non-neutral declarations. */
    private static DataTypeDeclaration dataTypeOf(OpcUaClient client, NodeId nodeId, int[] unknown)
            throws Exception {
        ReadValueId rv = new ReadValueId(
                nodeId, AttributeId.DataType.uid(), null, QualifiedName.NULL_VALUE);
        ReadResponse response = client.read(0.0, TimestampsToReturn.Neither, List.of(rv))
                .get(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        DataValue[] results = response.getResults();
        DataValue dataTypeValue = results != null && results.length > 0 ? results[0] : null;
        NodeId dataTypeId = dataTypeValue != null && dataTypeValue.getValue() != null
                && dataTypeValue.getValue().getValue() instanceof NodeId id ? id : null;
        String neutral = OpcUaTypes.neutralTypeOf(dataTypeId);
        if (neutral == null) {
            unknown[0]++;
        }
        String nativeTypeId = neutral == null && dataTypeId != null ? dataTypeId.toParseableString() : null;
        StructureDeclaration structure = nativeTypeId == null
                ? StructureDeclaration.EMPTY
                : readStructureDeclaration(client, dataTypeId);
        List<DataTypeEnumValueMsg> enumValues = nativeTypeId == null ? List.of() : readEnumValues(client, dataTypeId);
        return new DataTypeDeclaration(neutral, nativeTypeId,
                structure.members(), enumValues, structure.defaultEncodingId(),
                nativeTypeId == null ? List.of() : readDependentTypeDefinitions(client, structure.members()));
    }

    /** Reads the closure of custom field types, retaining opaque entries when no definition is exposed. */
    private static List<NativeDataTypeDefinitionMsg> readDependentTypeDefinitions(
            OpcUaClient client, List<DataTypeMemberMsg> members) {
        Map<String, NativeDataTypeDefinitionMsg> definitions = new LinkedHashMap<>();
        for (DataTypeMemberMsg member : members) {
            if (!member.getDataTypeNodeId().isBlank()) {
                readDependentTypeDefinition(client, member.getDataTypeNodeId(), definitions, new HashSet<>());
            }
        }
        return List.copyOf(definitions.values());
    }

    private static void readDependentTypeDefinition(
            OpcUaClient client,
            String typeId,
            Map<String, NativeDataTypeDefinitionMsg> definitions,
            Set<String> visiting) {
        if (definitions.containsKey(typeId) || !visiting.add(typeId)) {
            return;
        }
        try {
            NodeId nodeId = NodeId.parse(typeId);
            StructureDeclaration structure = readStructureDeclaration(client, nodeId);
            List<DataTypeEnumValueMsg> enumValues = readEnumValues(client, nodeId);
            NativeDataTypeDefinitionMsg definition = NativeDataTypeDefinitionMsg.newBuilder()
                    .setNodeId(typeId)
                    .setName(typeId)
                    .addAllMembers(structure.members())
                    .addAllEnumValues(enumValues)
                    .setDefaultEncodingId(structure.defaultEncodingId() == null ? "" : structure.defaultEncodingId())
                    .build();
            definitions.put(typeId, definition);
            for (DataTypeMemberMsg member : structure.members()) {
                if (!member.getDataTypeNodeId().isBlank()) {
                    readDependentTypeDefinition(client, member.getDataTypeNodeId(), definitions, visiting);
                }
            }
        } catch (Exception ignored) {
            // The caller still preserves the field's native NodeId as an opaque dependency.
            definitions.putIfAbsent(typeId, NativeDataTypeDefinitionMsg.newBuilder()
                    .setNodeId(typeId).setName(typeId).build());
        } finally {
            visiting.remove(typeId);
        }
    }

    /** Imports fields and the binary encoding required for lossless structure replay. */
    private static StructureDeclaration readStructureDeclaration(OpcUaClient client, NodeId dataTypeId)
            throws Exception {
        // DataTypeDefinition is AttributeId 23 in OPC UA 1.04. Milo 0.6 predates
        // that enum constant, but accepts the standard numeric id on the wire.
        ReadValueId request = new ReadValueId(dataTypeId, uint(23), null, QualifiedName.NULL_VALUE);
        ReadResponse response = client.read(0.0, TimestampsToReturn.Neither, List.of(request))
                .get(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        DataValue[] results = response.getResults();
        DataValue definitionValue = results != null && results.length > 0 ? results[0] : null;
        Object definition = definitionValue != null && definitionValue.getValue() != null
                ? definitionValue.getValue().getValue()
                : null;
        if (!(definition instanceof StructureDefinition structure) || structure.getFields() == null) {
            return StructureDeclaration.EMPTY;
        }
        List<DataTypeMemberMsg> members = new ArrayList<>();
        for (StructureField field : structure.getFields()) {
            String fieldName = field.getName();
            if (fieldName == null || fieldName.isBlank()) {
                continue;
            }
            NodeId fieldType = field.getDataType();
            String neutral = OpcUaTypes.neutralTypeOf(fieldType);
            DataTypeMemberMsg.Builder member = DataTypeMemberMsg.newBuilder().setName(fieldName);
            if (neutral != null) {
                member.setDataType(neutral);
            } else if (fieldType != null) {
                member.setDataTypeNodeId(fieldType.toParseableString());
            } else {
                continue;
            }
            int fieldValueRank = field.getValueRank() == null ? -1 : field.getValueRank();
            member.setValueRank(fieldValueRank >= 1 ? "ARRAY" : "SCALAR")
                    .setOptional(Boolean.TRUE.equals(field.getIsOptional()));
            if (field.getArrayDimensions() != null) {
                for (var dimension : field.getArrayDimensions()) {
                    if (dimension != null) {
                        member.addArrayDimensions(dimension.intValue());
                    }
                }
            }
            members.add(member.build());
        }
        NodeId encodingId = structure.getDefaultEncodingId();
        return new StructureDeclaration(List.copyOf(members),
                encodingId == null ? null : encodingId.toParseableString());
    }

    /** Imports numeric enum/option-set literals when the server exposes EnumValues. */
    private static List<DataTypeEnumValueMsg> readEnumValues(OpcUaClient client, NodeId dataTypeId) {
        try {
            var node = client.getAddressSpace().getNode(dataTypeId);
            if (!(node instanceof UaDataTypeNode dataTypeNode)) {
                return List.of();
            }
            EnumValueType[] values = dataTypeNode.readEnumValuesAsync()
                    .get(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (values == null) {
                return List.of();
            }
            List<DataTypeEnumValueMsg> literals = new ArrayList<>();
            for (EnumValueType value : values) {
                if (value == null || value.getValue() == null || value.getDisplayName() == null) {
                    continue;
                }
                String name = value.getDisplayName().getText();
                if (name == null || name.isBlank()) {
                    continue;
                }
                String description = value.getDescription() == null ? "" : value.getDescription().getText();
                literals.add(DataTypeEnumValueMsg.newBuilder()
                        .setName(name)
                        .setValue(value.getValue())
                        .setDescription(description == null ? "" : description)
                        .build());
            }
            return List.copyOf(literals);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static SchemaNodeMsg toNode(String nodeId, String parentId, String path, String name,
            boolean variable, DataTypeDeclaration declaration, String referenceType, VariableAttributes attrs) {
        SchemaNodeMsg.Builder b = SchemaNodeMsg.newBuilder()
                .setNodeId(nodeId)
                .setParentId(parentId == null ? "" : parentId)
                .setPath(path)
                .setName(name)
                .setKind(variable ? "VARIABLE" : "FOLDER");
        if (variable) {
            b.setDataType(declaration == null || declaration.neutralType() == null
                            ? "" : declaration.neutralType())
                    .setDataTypeNodeId(declaration == null || declaration.dataTypeNodeId() == null
                            ? "" : declaration.dataTypeNodeId())
                    .addAllDataTypeMembers(declaration == null ? List.of() : declaration.members())
                    .addAllDataTypeEnumValues(declaration == null ? List.of() : declaration.enumValues())
                    .setDataTypeDefaultEncodingId(declaration == null || declaration.defaultEncodingId() == null
                            ? "" : declaration.defaultEncodingId())
                    .addAllDataTypeDependencies(declaration == null ? List.of() : declaration.dependencies())
                    .setValueRank(attrs != null && attrs.valueRank() != null && attrs.valueRank() >= 0
                            ? "ARRAY" : "SCALAR")
                    .setAccess("READ");

            // IS-189: Add critical OPC UA attributes if available
            if (attrs != null) {
                if (attrs.accessLevel() != null) {
                    b.setAccessLevelFull(attrs.accessLevel());
                }
                if (attrs.minimumSamplingInterval() != null) {
                    b.setMinimumSamplingInterval((int) Math.round(attrs.minimumSamplingInterval()));
                }
                if (attrs.writeMask() != null) {
                    b.setWriteMask(attrs.writeMask());
                }
                if (attrs.historizing() != null) {
                    b.setHistorizing(attrs.historizing());
                }
            }
        }
        // IS-189: Preserve reference type from discovery (HAS_COMPONENT, HAS_PROPERTY, etc.)
        if (referenceType != null) {
            b.setReferenceType(referenceType);
        }
        return b.build();
    }

    private static String displayName(ReferenceDescription ref) {
        LocalizedText display = ref.getDisplayName();
        if (display != null && display.getText() != null && !display.getText().isBlank()) {
            return display.getText();
        }
        QualifiedName browse = ref.getBrowseName();
        if (browse != null && browse.getName() != null && !browse.getName().isBlank()) {
            return browse.getName();
        }
        return ref.getNodeId().toParseableString();
    }

}
