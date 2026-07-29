package com.ainclusive.iotsim.protocolmodel;

/**
 * Runtime capabilities for a native type declaration.
 *
 * <p>A false capability must carry an explanation so callers can surface an
 * actionable diagnostic rather than coercing an unsupported native value.
 */
public record NativeTypeCapability(
        boolean materializable,
        boolean captureDecodable,
        boolean replayEncodable,
        String unavailableReason) {

    public NativeTypeCapability {
        boolean unavailable = !materializable || !captureDecodable || !replayEncodable;
        if (unavailable && (unavailableReason == null || unavailableReason.isBlank())) {
            throw new IllegalArgumentException("unsupported native type capabilities require a reason");
        }
        if (!unavailable) {
            unavailableReason = null;
        }
    }

    public static NativeTypeCapability supported() {
        return new NativeTypeCapability(true, true, true, null);
    }

    public static NativeTypeCapability unsupported(String reason) {
        return new NativeTypeCapability(false, false, false, reason);
    }
}
