package com.mudassir.eka.domain.shared;

public record PageRequest(int pageNumber, int pageSize) {

    public PageRequest {
        if (pageNumber < 0) throw new IllegalArgumentException("pageNumber must be >= 0");
        if (pageSize < 1 || pageSize > 200) throw new IllegalArgumentException("pageSize must be between 1 and 200");
    }

    public static PageRequest of(int pageNumber, int pageSize) {
        return new PageRequest(pageNumber, pageSize);
    }

    public static PageRequest first(int pageSize) {
        return new PageRequest(0, pageSize);
    }

    public int offset() {
        return pageNumber * pageSize;
    }
}
