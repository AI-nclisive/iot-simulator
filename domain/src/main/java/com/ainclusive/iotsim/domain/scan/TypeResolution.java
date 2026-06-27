package com.ainclusive.iotsim.domain.scan;

/**
 * A user's decision for one discovered node whose data type a scan could not map to
 * the neutral set ("unknown type" — backend-specs/01 §2). Unknown-typed variables
 * <b>require</b> a resolution before a schema can be created from the scan (IS-044):
 * either assign a neutral {@code dataType} (the node is kept as a VARIABLE) or
 * {@code exclude} it (the node is dropped).
 *
 * <p>Fields are strings (parsed/validated by {@link ScanService}) to mirror
 * {@link com.ainclusive.iotsim.platform.scan.DiscoveredNode}; an invalid enum value
 * surfaces as a 400 via {@code IllegalArgumentException}.
 *
 * @param nodeId    the discovered node this resolution targets (required)
 * @param dataType  neutral data type to assign; required unless {@code exclude}
 * @param valueRank optional {@code SCALAR}/{@code ARRAY}; defaults to the discovered rank
 * @param access    optional {@code READ}/{@code READ_WRITE}; defaults to the discovered access
 * @param exclude   when {@code true}, drop the node instead of assigning a type
 */
public record TypeResolution(
        String nodeId, String dataType, String valueRank, String access, boolean exclude) {

    public TypeResolution {
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("resolution nodeId is required");
        }
        nodeId = nodeId.strip();
        if (!exclude && (dataType == null || dataType.isBlank())) {
            throw new IllegalArgumentException(
                    "resolution for " + nodeId + " needs a dataType unless it is excluded");
        }
    }
}
