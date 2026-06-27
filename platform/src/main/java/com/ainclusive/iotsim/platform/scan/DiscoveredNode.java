package com.ainclusive.iotsim.platform.scan;

/**
 * One node discovered by a scan, in protocol-neutral terms. Distinct from
 * {@code protocol-model.SchemaNode}: a discovered VARIABLE may have a {@code null}
 * {@code dataType} ("unknown" — outside the neutral type set), which a SchemaNode
 * forbids. Unknown types are surfaced for the user to resolve before they become a
 * persisted schema (backend-specs/01 §2; resolution is IS-044).
 *
 * @param kind {@code FOLDER} or {@code VARIABLE}
 * @param dataType neutral data type for a VARIABLE, or {@code null} if unknown
 */
public record DiscoveredNode(String nodeId, String parentId, String path, String name,
        String kind, String dataType, String valueRank, String access,
        String unit, String description) {

    /** True for a VARIABLE whose type could not be mapped to the neutral set. */
    public boolean isUnknownType() {
        return "VARIABLE".equals(kind) && (dataType == null || dataType.isBlank());
    }
}
