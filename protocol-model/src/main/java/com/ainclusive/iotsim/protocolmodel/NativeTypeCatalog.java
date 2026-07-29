package com.ainclusive.iotsim.protocolmodel;

import java.util.List;

/**
 * Compatibility view over schema-native declarations.
 *
 * <p>IS-192 stored native declarations as top-level {@link NodeKind#DATA_TYPE}
 * schema nodes. IS-194 exposes those declarations as a catalog without changing
 * existing schema versions; persistence can later store the same model directly.
 */
public final class NativeTypeCatalog {

    private NativeTypeCatalog() {}

    public static List<NativeTypeDefinition> fromSchemaNodes(List<SchemaNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        return nodes.stream()
                .filter(node -> node.kind() == NodeKind.DATA_TYPE)
                .map(NativeTypeCatalog::fromSchemaNode)
                .toList();
    }

    private static NativeTypeDefinition fromSchemaNode(SchemaNode node) {
        NativeTypeKind kind = node.nativeTypeKind();
        List<NativeTypeField> fields = node.members().stream()
                .map(member -> new NativeTypeField(member.name(), member.dataType(), member.dataTypeNodeId(),
                        member.valueRank(), member.arrayDimensions(), member.optional(), null))
                .toList();
        NativeTypeCapability capability = kind == NativeTypeKind.UNION
                ? NativeTypeCapability.unsupported(
                        "the OPC UA stack did not expose the union discriminator metadata required for replay")
                : kind == NativeTypeKind.OPTION_SET
                ? NativeTypeCapability.unsupported(
                        "option-set bit metadata cannot yet be materialized by the OPC UA runtime")
                : kind == NativeTypeKind.OPAQUE
                ? NativeTypeCapability.unsupported("the source did not supply a native type definition")
                : kind == NativeTypeKind.STRUCTURE
                        && (node.defaultEncodingId() == null || node.defaultEncodingId().isBlank())
                ? NativeTypeCapability.unsupported("no default binary encoding was supplied")
                : NativeTypeCapability.supported();
        return new NativeTypeDefinition(
                node.nodeId(), null, node.nodeId(), node.name(), node.name(), node.description(), kind,
                null, node.defaultEncodingId(), null, fields, node.enumValues(), capability);
    }
}
