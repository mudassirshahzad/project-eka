package com.mudassir.eka.domain.shared;

import java.util.List;

public record PageResult<T>(
        List<T> content,
        int pageNumber,
        int pageSize,
        long totalElements
) {

    public static <T> PageResult<T> of(List<T> content, int pageNumber, int pageSize, long totalElements) {
        return new PageResult<>(content, pageNumber, pageSize, totalElements);
    }

    public boolean hasNext() {
        return (long) (pageNumber + 1) * pageSize < totalElements;
    }

    public int totalPages() {
        return pageSize == 0 ? 0 : (int) Math.ceil((double) totalElements / pageSize);
    }
}
