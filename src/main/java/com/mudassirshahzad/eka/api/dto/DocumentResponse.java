package com.mudassirshahzad.eka.api.dto;

import com.mudassirshahzad.eka.domain.document.Document;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record DocumentResponse(
        UUID        id,
        String      filename,
        String      format,
        String      status,
        int         chunkCount,
        String      ingestionError,
        String      title,
        String      department,
        String      classification,
        Set<String> tags,
        Instant     createdAt,
        Instant     updatedAt
) {

    public DocumentResponse {
        Objects.requireNonNull(id,        "id must not be null");
        Objects.requireNonNull(filename,  "filename must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId().value(),
                document.getFilename(),
                document.getFormat().name(),
                document.getStatus().name(),
                document.getChunkCount(),
                document.getIngestionError(),
                document.getMetadata().title(),
                document.getMetadata().department(),
                document.getMetadata().classification(),
                document.getMetadata().tags(),
                document.getCreatedAt(),
                document.getUpdatedAt());
    }
}
