package com.ainclusive.iotsim.domain.manualschema;

import com.ainclusive.iotsim.protocolmodel.Access;
import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.ReferenceType;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import com.ainclusive.iotsim.protocolmodel.SchemaReference;
import com.ainclusive.iotsim.protocolmodel.ValueRank;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Imports the portable subset of OPC UA NodeSet XML without silently flattening its hierarchy. */
public final class OpcUaNodeSetImporter {
    private static final Set<String> HIERARCHICAL = Set.of("Organizes", "HasComponent", "HasProperty");

    private OpcUaNodeSetImporter() {}

    public static Result importXml(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("NodeSet XML is required");
        }
        Document document = parse(xml);
        List<Diagnostic> diagnostics = new ArrayList<>();
        List<RawNode> rawNodes = new ArrayList<>();
        NodeList children = document.getDocumentElement().getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (!(children.item(i) instanceof Element element)) {
                continue;
            }
            NodeKind kind = kindOf(element.getLocalName());
            if (kind == null) {
                if (element.getLocalName() != null && element.getLocalName().startsWith("UA")) {
                    diagnostics.add(Diagnostic.unsupported("node", element.getLocalName(), "Node kind is not supported"));
                }
                continue;
            }
            String nodeId = element.getAttribute("NodeId");
            if (nodeId.isBlank()) {
                diagnostics.add(Diagnostic.unsupported("node", element.getLocalName(), "NodeId is required"));
                continue;
            }
            DataType dataType = kind == NodeKind.VARIABLE ? dataTypeOf(element.getAttribute("DataType")) : null;
            if (kind == NodeKind.VARIABLE && dataType == null) {
                diagnostics.add(Diagnostic.unsupported("variable", nodeId,
                        "DataType '" + element.getAttribute("DataType") + "' is not supported"));
                continue;
            }
            // IS-189: Extract critical OPC UA attributes
            rawNodes.add(new RawNode(nodeId, browseName(element), kind, dataType, valueRank(element),
                    access(element), references(element, nodeId, diagnostics),
                    accessLevelFull(element), minimumSamplingInterval(element),
                    writeMask(element), historizing(element)));
        }
        return materialize(rawNodes, diagnostics);
    }

    private static Result materialize(List<RawNode> rawNodes, List<Diagnostic> diagnostics) {
        Map<String, RawNode> byId = new HashMap<>();
        rawNodes.forEach(node -> byId.put(node.nodeId, node));
        Map<String, String> parents = new HashMap<>();
        for (RawNode source : rawNodes) {
            for (RawReference reference : source.references) {
                if (!HIERARCHICAL.contains(reference.type)) {
                    continue;
                }
                String child = reference.forward ? reference.target : source.nodeId;
                String parent = reference.forward ? source.nodeId : reference.target;
                if (byId.containsKey(child) && byId.containsKey(parent)) {
                    String previous = parents.putIfAbsent(child, parent);
                    if (previous != null && !previous.equals(parent)) {
                        diagnostics.add(Diagnostic.unsupported("reference", child,
                                "Multiple hierarchical parents; using " + previous));
                    }
                }
            }
        }
        List<SchemaNode> nodes = new ArrayList<>();
        for (RawNode raw : rawNodes) {
            String path = pathOf(raw.nodeId, byId, parents, new LinkedHashSet<>());
            if (path == null) {
                diagnostics.add(Diagnostic.unsupported("node", raw.nodeId,
                        "Cyclic hierarchical references; node and its descendants were skipped"));
                continue;
            }
            List<SchemaReference> references = raw.references.stream()
                    .filter(reference -> byId.containsKey(reference.target))
                    .map(reference -> new SchemaReference(reference.target, referenceType(reference.type), reference.forward))
                    .toList();
            // IS-189: Include critical OPC UA attributes
            nodes.add(new SchemaNode(raw.nodeId, parents.get(raw.nodeId), path, raw.name, raw.kind, raw.dataType,
                    raw.valueRank, raw.access, null, null, List.of(), null, references,
                    null, List.of(),  // dataTypeNodeId, members (not used in NodeSet import)
                    raw.accessLevelFull, raw.minimumSamplingInterval, raw.writeMask, raw.historizing));
        }
        Set<String> importedIds = nodes.stream().map(SchemaNode::nodeId).collect(java.util.stream.Collectors.toSet());
        // IS-189: Preserve attributes when filtering references
        nodes = nodes.stream().map(node -> new SchemaNode(
                node.nodeId(), node.parentId(), node.path(), node.name(), node.kind(), node.dataType(),
                node.valueRank(), node.access(), node.unit(), node.description(), node.arrayDimensions(),
                node.typeDefinition(), node.references().stream()
                        .filter(reference -> importedIds.contains(reference.targetNodeId()))
                        .toList(),
                node.dataTypeNodeId(), node.members(),
                node.accessLevelFull(), node.minimumSamplingInterval(), node.writeMask(), node.historizing())).toList();
        return new Result(List.copyOf(nodes), List.copyOf(diagnostics));
    }

    private static String pathOf(String id, Map<String, RawNode> byId, Map<String, String> parents, Set<String> seen) {
        if (!seen.add(id)) {
            return null;
        }
        RawNode node = byId.get(id);
        String parent = parents.get(id);
        if (parent == null) {
            return node.name;
        }
        String parentPath = pathOf(parent, byId, parents, seen);
        return parentPath == null ? null : parentPath + "/" + node.name;
    }

    private static List<RawReference> references(Element element, String nodeId, List<Diagnostic> diagnostics) {
        List<RawReference> result = new ArrayList<>();
        NodeList referenceElements = element.getElementsByTagNameNS("*", "Reference");
        for (int i = 0; i < referenceElements.getLength(); i++) {
            Element reference = (Element) referenceElements.item(i);
            String target = reference.getTextContent().trim();
            String type = shortReferenceType(reference.getAttribute("ReferenceType"));
            if (target.isBlank()) {
                diagnostics.add(Diagnostic.unsupported("reference", nodeId, "Reference target is missing"));
                continue;
            }
            result.add(new RawReference(target, type, !"false".equalsIgnoreCase(reference.getAttribute("IsForward"))));
        }
        return result;
    }

    private static String shortReferenceType(String value) {
        String normalized = numericIdentifier(value).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "i=35", "organizes" -> "Organizes";
            case "i=47", "hascomponent" -> "HasComponent";
            case "i=46", "hasproperty" -> "HasProperty";
            case "i=40", "hastypedefinition" -> "HasTypeDefinition";
            default -> "Generic";
        };
    }

    private static ReferenceType referenceType(String type) {
        return switch (type) {
            case "Organizes" -> ReferenceType.ORGANIZES;
            case "HasComponent" -> ReferenceType.HAS_COMPONENT;
            case "HasProperty" -> ReferenceType.HAS_PROPERTY;
            case "HasTypeDefinition" -> ReferenceType.HAS_TYPE_DEFINITION;
            default -> ReferenceType.GENERIC;
        };
    }

    private static NodeKind kindOf(String name) {
        return switch (name) {
            case "UAObject" -> NodeKind.OBJECT;
            case "UAVariable" -> NodeKind.VARIABLE;
            case "UAMethod" -> NodeKind.METHOD;
            default -> null;
        };
    }

    private static String browseName(Element element) {
        String browseName = element.getAttribute("BrowseName");
        int separator = browseName.indexOf(':');
        return separator >= 0 ? browseName.substring(separator + 1) : browseName;
    }

    private static ValueRank valueRank(Element element) {
        String rank = element.getAttribute("ValueRank");
        return rank.isBlank() || "-1".equals(rank) ? ValueRank.SCALAR : ValueRank.ARRAY;
    }

    private static Access access(Element element) {
        String level = element.getAttribute("AccessLevel");
        try {
            return !level.isBlank() && (Integer.decode(level) & 2) != 0 ? Access.READ_WRITE : Access.READ;
        } catch (NumberFormatException ignored) {
            return Access.READ;
        }
    }

    // IS-189: Parse critical OPC UA attributes from NodeSet XML
    private static Integer accessLevelFull(Element element) {
        String level = element.getAttribute("AccessLevel");
        if (level.isBlank()) {
            return null;
        }
        try {
            return Integer.decode(level);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer minimumSamplingInterval(Element element) {
        String interval = element.getAttribute("MinimumSamplingInterval");
        if (interval.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(interval);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer writeMask(Element element) {
        String mask = element.getAttribute("WriteMask");
        if (mask.isBlank()) {
            return null;
        }
        try {
            return Integer.decode(mask);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Boolean historizing(Element element) {
        String value = element.getAttribute("Historizing");
        if (value.isBlank()) {
            return null;
        }
        return "true".equalsIgnoreCase(value);
    }

    private static DataType dataTypeOf(String dataType) {
        String id = numericIdentifier(dataType);
        return switch (id) {
            case "i=1" -> DataType.BOOL; case "i=2" -> DataType.INT8; case "i=3" -> DataType.UINT8;
            case "i=4" -> DataType.INT16; case "i=5" -> DataType.UINT16; case "i=6" -> DataType.INT32;
            case "i=7" -> DataType.UINT32; case "i=8" -> DataType.INT64; case "i=9" -> DataType.UINT64;
            case "i=10" -> DataType.FLOAT32; case "i=11" -> DataType.FLOAT64; case "i=12" -> DataType.STRING;
            case "i=13" -> DataType.DATETIME; case "i=14" -> DataType.GUID; case "i=15" -> DataType.BYTES;
            case "i=16" -> DataType.XML_ELEMENT; case "i=17" -> DataType.NODE_ID; case "i=18" -> DataType.EXPANDED_NODE_ID;
            case "i=19" -> DataType.STATUS_CODE; case "i=20" -> DataType.QUALIFIED_NAME; case "i=21" -> DataType.LOCALIZED_TEXT;
            default -> null;
        };
    }

    private static Document parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setNamespaceAware(true);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid NodeSet XML: " + e.getMessage(), e);
        }
    }

    private static String numericIdentifier(String value) {
        int numeric = value.lastIndexOf("i=");
        return numeric >= 0 ? value.substring(numeric) : value;
    }

    public record Result(List<SchemaNode> nodes, List<Diagnostic> diagnostics) {}
    public record Diagnostic(String severity, String subject, String identifier, String message) {
        static Diagnostic unsupported(String subject, String identifier, String message) {
            return new Diagnostic("UNSUPPORTED", subject, identifier, message);
        }
    }
    // IS-189: Extended with critical OPC UA attributes
    private record RawNode(String nodeId, String name, NodeKind kind, DataType dataType, ValueRank valueRank,
            Access access, List<RawReference> references, Integer accessLevelFull, Integer minimumSamplingInterval,
            Integer writeMask, Boolean historizing) {}
    private record RawReference(String target, String type, boolean forward) {}
}
