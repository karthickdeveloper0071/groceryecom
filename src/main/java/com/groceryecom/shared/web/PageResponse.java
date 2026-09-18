package com.groceryecom.shared.web;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * One page of a collection, in the shape every list endpoint returns.
 *
 * <p>Spring Data's own {@code Page} is deliberately not sent to clients: its JSON is
 * large, it carries the sort and pageable objects nobody uses, and its shape has changed
 * between Spring versions. This is ours, it is small, and it will not move under a client
 * that has shipped.
 *
 * <p>Zero-based pages, because that is what the database, Spring Data and every client
 * library already use; a UI that shows "page 1" adds one when it renders, which is a
 * display decision.
 *
 * <p>{@code hasNext} is included although a client could compute it. Paging code that has
 * to do arithmetic to know whether to enable a button is paging code with an off-by-one
 * in it.
 *
 * @param items      the rows on this page
 * @param page       zero-based page number
 * @param size       how many rows were asked for
 * @param totalItems how many rows match in total
 * @param totalPages how many pages that makes
 * @param hasNext    whether another page exists
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages,
                              boolean hasNext) {

    /** Maps a Spring Data page of entities to a page of responses in one step. */
    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> toResponse) {
        return new PageResponse<>(
                page.getContent().stream().map(toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext());
    }
}
