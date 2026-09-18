package com.groceryecom.shared.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * The paging parameters a list endpoint accepts, with limits.
 *
 * <p>The limit is the point. An endpoint that lets a caller ask for
 * {@code ?size=1000000} hands anyone a way to read a million rows into memory and stall
 * the database for everybody else; it is one of the easiest denial-of-service holes to
 * leave open, and it is usually left open by accident.
 *
 * <p>Spring can bind {@code Pageable} directly, but its defaults are global and its
 * maximum is silent. This is explicit, so a reader of the controller can see what a
 * caller may ask for.
 */
public final class PageRequestParams {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private PageRequestParams() {
    }

    /**
     * @param page zero-based; anything below zero is treated as the first page
     * @param size clamped to {@value #MAX_SIZE}, because a caller does not decide how
     *             much work the database does
     */
    public static Pageable of(int page, int size, Sort sort) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size <= 0 ? DEFAULT_SIZE : size, 1, MAX_SIZE);

        return PageRequest.of(safePage, safeSize, sort);
    }

    /** Newest first, which is what every admin queue in this platform wants. */
    public static Pageable newestFirst(int page, int size) {
        return of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    /** Oldest first: a work queue is worked through in the order things arrived. */
    public static Pageable oldestFirst(int page, int size) {
        return of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
    }
}
