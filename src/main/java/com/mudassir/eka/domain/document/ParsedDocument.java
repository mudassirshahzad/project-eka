package com.mudassir.eka.domain.document;

import java.time.Instant;

public record ParsedDocument(
        String          extractedText,
        ParsedMetadata  metadata,
        SupportedFormat detectedFormat,
        ParsingStatus   status,
        Instant         parsedAt
) {}
