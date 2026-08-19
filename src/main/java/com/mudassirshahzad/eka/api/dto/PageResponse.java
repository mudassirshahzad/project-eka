package com.mudassirshahzad.eka.api.dto;

import com.mudassirshahzad.eka.domain.shared.PageResult;

import java.util.Objects;
import java.util.function.Function;

/**
 * Single paginated-list response shape shared by every list endpoint (P06.1 — introduced once two
 * independent callers, document listing and conversation listing, needed it in the same milestone;
 * consistency review, not speculative infrastructure).
 */
public record PageResponse<T>(
        java.util.List<T> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public PageResponse {
        Objects.requireNonNull(content, "content must not be null");
    }

    public static <D, T> PageResponse<T> from(PageResult<D> page, Function<D, T> mapper) {
        return new PageResponse<>(
                page.content().stream().map(mapper).toList(),
                page.pageNumber(),
                page.pageSize(),
                page.totalElements(),
                page.totalPages(),
                page.hasNext());
    }
}
