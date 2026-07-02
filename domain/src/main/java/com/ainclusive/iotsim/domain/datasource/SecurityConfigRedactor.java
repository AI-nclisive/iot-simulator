package com.ainclusive.iotsim.domain.datasource;

/** Public API-boundary view of a stored endpoint-security config: strips password hashes. */
public final class SecurityConfigRedactor {

    private SecurityConfigRedactor() {}

    /** Returns the storage JSON with all password hashes removed (usernames + flags only). */
    public static String redact(String storedSecurityConfigJson) {
        return EndpointSecurityCodec.redact(storedSecurityConfigJson);
    }
}
