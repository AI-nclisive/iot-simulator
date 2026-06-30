package com.ainclusive.iotsim.domain.support;

import java.util.List;
import java.util.function.Function;

/** Cursor-paged collection response (IS-074). {@code nextCursor} is null on the last page. */
public record Page<T>(List<T> items, String nextCursor, int limit) {

    public <R> Page<R> map(Function<T, R> mapper) {
        return new Page<>(items.stream().map(mapper).toList(), nextCursor, limit);
    }
}
