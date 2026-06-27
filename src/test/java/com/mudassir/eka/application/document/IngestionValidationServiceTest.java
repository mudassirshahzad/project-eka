package com.mudassir.eka.application.document;

import com.mudassir.eka.domain.chunk.Chunk;
import com.mudassir.eka.domain.chunk.ChunkMetadata;
import com.mudassir.eka.domain.chunk.ChunkId;
import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.domain.shared.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IngestionValidationServiceTest {

    private final IngestionValidationService service = new IngestionValidationService();
    private final DocumentId docId    = DocumentId.generate();
    private final TenantId   tenantId = TenantId.generate();

    private Chunk indexedChunk(String vectorId) {
        return Chunk.reconstitute(
                ChunkId.generate(), docId, tenantId, 0, "text",
                ChunkMetadata.of("sliding-window"),
                vectorId, "nomic-embed-text", 768, Instant.now(), Instant.now());
    }

    private Chunk unindexedChunk() {
        return Chunk.reconstitute(
                ChunkId.generate(), docId, tenantId, 0, "text",
                ChunkMetadata.of("sliding-window"),
                null, "nomic-embed-text", 768, Instant.now(), Instant.now());
    }

    private Chunk unembeddedChunk() {
        return Chunk.reconstitute(
                ChunkId.generate(), docId, tenantId, 0, "text",
                ChunkMetadata.of("sliding-window"),
                "v1", null, null, null, Instant.now());
    }

    @Test
    void validate_returnsValid_whenAllChunksIndexedWithProvenance() {
        List<Chunk> chunks = List.of(indexedChunk("v1"), indexedChunk("v2"));

        IngestionValidationResult result = service.validate(chunks, 2);

        assertThat(result.valid()).isTrue();
        assertThat(result.violations()).isEmpty();
        assertThat(result.indexedCount()).isEqualTo(2);
    }

    @Test
    void validate_reportsViolation_whenCountMismatch() {
        List<Chunk> chunks = List.of(indexedChunk("v1"));

        IngestionValidationResult result = service.validate(chunks, 3);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("Expected 3 chunk(s) but got 1"));
    }

    @Test
    void validate_reportsViolation_whenNullVectorIds() {
        List<Chunk> chunks = List.of(indexedChunk("v1"), unindexedChunk());

        IngestionValidationResult result = service.validate(chunks, 2);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("null vectorId"));
        assertThat(result.indexedCount()).isEqualTo(1);
    }

    @Test
    void validate_reportsViolation_whenDuplicateVectorIds() {
        List<Chunk> chunks = List.of(indexedChunk("same-id"), indexedChunk("same-id"));

        IngestionValidationResult result = service.validate(chunks, 2);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("Duplicate vectorIds"));
    }

    @Test
    void validate_reportsViolation_whenEmbeddingProvenanceMissing() {
        List<Chunk> chunks = List.of(unembeddedChunk());

        IngestionValidationResult result = service.validate(chunks, 1);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("embedding provenance"));
    }
}
