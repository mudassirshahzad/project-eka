package com.mudassirshahzad.eka.api.dto;

import com.mudassirshahzad.eka.domain.conversation.Citation;

import java.util.Objects;
import java.util.UUID;

public record CitationResponse(UUID chunkId, double relevanceScore) {

    public CitationResponse {
        Objects.requireNonNull(chunkId, "chunkId must not be null");
    }

    public static CitationResponse from(Citation citation) {
        return new CitationResponse(citation.chunkId().value(), citation.relevanceScore());
    }
}
