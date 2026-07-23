package com.ainclusive.iotsim.worker.opcua;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import com.ainclusive.iotsim.workercontract.v1.SchemaNodeMsg;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
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
import org.eclipse.milo.opcua.stack.core.types.structured.ReadResponse;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;

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
                // A Variable's HasProperty children (EURange, EngineeringUnits,
                // EnumStrings, Quality, ...) are metadata about the value, not part of
                // it, and are extremely common on AnalogItemType/DataItemType
                // variables. They're skipped entirely (IS-188) — not just left
                // un-descended-into: the neutral schema model only allows a
                // FOLDER/OBJECT parent (SchemaNodeValidator), and a Property's parent
                // is always its owning Variable, so emitting them as discovered nodes
                // made every such real device un-scannable ("parent must be a FOLDER
                // or OBJECT").
                if (isVariable && Identifiers.HasProperty.equals(ref.getReferenceTypeId())) {
                    continue;
                }
                if (nodes.size() >= cap) {
                    truncated = true;
                    return new ScanOutcome(PARTIAL, List.copyOf(nodes), truncated, unknown[0],
                            "stopped at " + cap + " discovered nodes");
                }
                String name = displayName(ref);
                String neutralId = childId.toParseableString();
                String path = frame.path().isEmpty() ? name : frame.path() + "/" + name;
                nodes.add(toNode(neutralId, frame.parentId(), path, name, isVariable,
                        isVariable ? neutralTypeOf(client, childId, unknown) : null));
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

    /** Reads a variable's DataType attribute and reverse-maps it; counts unknowns. */
    private static String neutralTypeOf(OpcUaClient client, NodeId nodeId, int[] unknown)
            throws Exception {
        ReadValueId rv = new ReadValueId(
                nodeId, AttributeId.DataType.uid(), null, QualifiedName.NULL_VALUE);
        ReadResponse response = client.read(0.0, TimestampsToReturn.Neither, List.of(rv))
                .get(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        DataValue[] results = response.getResults();
        NodeId dataTypeId = results != null && results.length > 0
                && results[0].getValue().getValue() instanceof NodeId id ? id : null;
        String neutral = OpcUaTypes.neutralTypeOf(dataTypeId);
        if (neutral == null) {
            unknown[0]++;
        }
        return neutral;
    }

    private static SchemaNodeMsg toNode(String nodeId, String parentId, String path, String name,
            boolean variable, String dataType) {
        SchemaNodeMsg.Builder b = SchemaNodeMsg.newBuilder()
                .setNodeId(nodeId)
                .setParentId(parentId == null ? "" : parentId)
                .setPath(path)
                .setName(name)
                .setKind(variable ? "VARIABLE" : "FOLDER");
        if (variable) {
            b.setDataType(dataType == null ? "" : dataType) // empty = unknown type
                    .setValueRank("SCALAR")
                    .setAccess("READ");
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
