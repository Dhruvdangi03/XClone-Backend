package com.project.Xclone_backend.common;

import java.util.List;
import java.util.function.Function;

/**
 * Keyset page. {@code nextCursor} is the id to pass as {@code cursor} for the next page, or null when exhausted.
 */
public record CursorPage<T>(List<T> items, Long nextCursor) {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 50;

    public static int clampLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    /** Use with a cursor value when none was given, so "id < cursor" matches everything. */
    public static long cursorOrMax(Long cursor) {
        return cursor == null ? Long.MAX_VALUE : cursor;
    }

    /**
     * Builds a page from rows fetched with {@code limit + 1}; the extra row only signals that more exist.
     */
    public static <E, T> CursorPage<T> of(List<E> rows, int limit, Function<E, Long> idOf, Function<List<E>, List<T>> mapper) {
        boolean hasMore = rows.size() > limit;
        List<E> page = hasMore ? rows.subList(0, limit) : rows;
        Long next = hasMore ? idOf.apply(page.get(page.size() - 1)) : null;
        return new CursorPage<>(mapper.apply(page), next);
    }
}
