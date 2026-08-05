package com.ainclusive.iotsim.platform;

import java.util.UUID;

/**
 * Entity id generation. Ids are opaque, stable strings.
 *
 * <p>Today's implementation is UUIDv4. A sortable form (e.g. ULID) can replace it
 * here without touching callers; no spec requires one, so this stays UUIDv4 until
 * a task asks otherwise.
 */
public final class Ids {

    private Ids() {}

    public static String newId() {
        return UUID.randomUUID().toString();
    }
}
