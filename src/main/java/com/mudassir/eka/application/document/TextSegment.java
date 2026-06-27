package com.mudassir.eka.application.document;

public record TextSegment(
        String content,
        int    startOffset,
        int    endOffset,
        int    tokenCount,
        int    sequenceNumber
) {}
