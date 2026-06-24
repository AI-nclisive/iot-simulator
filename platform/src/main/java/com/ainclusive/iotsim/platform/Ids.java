package com.ainclusive.iotsim.platform;

import java.util.UUID;

/**
 * Entity id generation. Ids are opaque, stable strings.
 *
 * <p>Scaffold uses UUIDv4; backend-specs target ULID (sortable) — swap the
 * implementation here without touching callers.
 */
public final class Ids {

    private Ids() {}

    public static String newId() {
        return UUID.randomUUID().toString();
    }
}
