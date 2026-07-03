package com.ainclusive.iotsim.protocolmodel;

import java.time.Instant;
import java.util.List;

/**
 * Optional filter criteria for value timeline queries (IS-136).
 * All fields are nullable/empty — an empty filter matches all rows.
 */
public record ValueFilter(
        String search,
        List<Quality> qualities,
        Instant from,
        Instant to) {

    public static final ValueFilter NONE = new ValueFilter(null, List.of(), null, null);

    public ValueFilter {
        if (qualities == null) {
            qualities = List.of();
        }
    }

    public boolean isBlank() {
        return (search == null || search.isBlank())
                && qualities.isEmpty()
                && from == null
                && to == null;
    }
}
