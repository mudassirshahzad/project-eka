package com.mudassir.eka.domain.document;

public record ParsedMetadata(
        String title,
        String author,
        String description,
        int    pageCount,
        long   characterCount
) {}
