package com.ainclusive.iotsim.protocolmodel;

import java.util.List;
import java.util.Objects;

/**
 * A schema-owned native type declaration retained independently of address-space nodes.
 *
 * <p>The stable {@code typeId} is referenced by variables and fields. {@code nativeNodeId}
 * preserves the OPC UA declaration identity supplied by the endpoint or standard catalog.
 */
public record NativeTypeDefinition(
        String typeId,
        String namespaceUri,
        String nativeNodeId,
        String browseName,
        String displayName,
        String description,
        NativeTypeKind kind,
        String baseTypeId,
        String defaultBinaryEncodingId,
        String defaultXmlEncodingId,
        List<NativeTypeField> fields,
        List<DataTypeEnumValue> enumValues,
        NativeTypeCapability capability) {

    public NativeTypeDefinition {
        requireText(typeId, "typeId");
        requireText(nativeNodeId, "nativeNodeId");
        requireText(browseName, "browseName");
        Objects.requireNonNull(kind, "kind");
        fields = fields == null ? List.of() : List.copyOf(fields);
        enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
        capability = capability == null ? NativeTypeCapability.supported() : capability;
        if ((kind == NativeTypeKind.STRUCTURE || kind == NativeTypeKind.UNION) && fields.isEmpty()) {
            throw new IllegalArgumentException(kind + " native type requires fields");
        }
        if ((kind == NativeTypeKind.ENUM || kind == NativeTypeKind.OPTION_SET) && enumValues.isEmpty()) {
            throw new IllegalArgumentException(kind + " native type requires enum values");
        }
        if ((kind == NativeTypeKind.ENUM || kind == NativeTypeKind.OPTION_SET || kind == NativeTypeKind.OPAQUE)
                && !fields.isEmpty()) {
            throw new IllegalArgumentException(kind + " native type cannot declare fields");
        }
        if ((kind == NativeTypeKind.STRUCTURE || kind == NativeTypeKind.UNION || kind == NativeTypeKind.OPAQUE)
                && !enumValues.isEmpty()) {
            throw new IllegalArgumentException(kind + " native type cannot declare enum values");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
