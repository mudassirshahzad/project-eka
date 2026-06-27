package com.mudassir.eka.infrastructure.retrieval.weaviate;

import com.mudassir.eka.domain.chunk.Chunk;
import com.mudassir.eka.domain.chunk.ChunkId;
import com.mudassir.eka.domain.chunk.ChunkMetadata;
import com.mudassir.eka.domain.chunk.VectorSearchResult;
import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.domain.retrieval.model.RetrievedChunk;
import com.mudassir.eka.domain.shared.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievedChunkMapperTest {

    private final RetrievedChunkMapper mapper = new RetrievedChunkMapper();

    private final TenantId   tenantId   = TenantId.generate();
    private final DocumentId documentId = DocumentId.generate();

    @Test
    void toRetrievedChunk_mapsAllFieldsCorrectly() {
        ChunkId chunkId = ChunkId.generate();
        Chunk   chunk   = sampleChunk(chunkId);
        VectorSearchResult raw = new VectorSearchResult(chunkId, "vector-42", "text content", 0.87);

        RetrievedChunk result = mapper.toRetrievedChunk(raw, chunk, 0);

        assertThat(result.chunkId()).isEqualTo(chunk.getId());
        assertThat(result.documentId()).isEqualTo(chunk.getDocumentId());
        assertThat(result.tenantId()).isEqualTo(chunk.getTenantId());
        assertThat(result.content()).isEqualTo("text content");
        assertThat(result.score()).isEqualTo(0.87);
        assertThat(result.rank()).isEqualTo(0);
    }

    @Test
    void toRetrievedChunk_usesChunkIdsNotVectorSearchResultIds() {
        ChunkId chunkId = ChunkId.generate();
        Chunk   chunk   = sampleChunk(chunkId);
        VectorSearchResult raw = new VectorSearchResult(chunkId, "vec-id", "content", 0.9);

        RetrievedChunk result = mapper.toRetrievedChunk(raw, chunk, 0);

        assertThat(result.chunkId()).isEqualTo(chunk.getId());
        assertThat(result.documentId()).isEqualTo(documentId);
        assertThat(result.tenantId()).isEqualTo(tenantId);
    }

    @Test
    void toRetrievedChunk_assignsSpecifiedRank() {
        Chunk chunk = sampleChunk(ChunkId.generate());
        VectorSearchResult raw = new VectorSearchResult(chunk.getId(), "v", "c", 0.7);

        assertThat(mapper.toRetrievedChunk(raw, chunk, 3).rank()).isEqualTo(3);
        assertThat(mapper.toRetrievedChunk(raw, chunk, 0).rank()).isEqualTo(0);
    }

    @Test
    void clampToUnitRange_clampsBelowZeroToZero() {
        assertThat(mapper.clampToUnitRange(-0.1)).isEqualTo(0.0);
        assertThat(mapper.clampToUnitRange(-99.0)).isEqualTo(0.0);
    }

    @Test
    void clampToUnitRange_clampsAboveOneToOne() {
        assertThat(mapper.clampToUnitRange(1.001)).isEqualTo(1.0);
        assertThat(mapper.clampToUnitRange(2.5)).isEqualTo(1.0);
    }

    @Test
    void clampToUnitRange_preservesScoreWithinRange() {
        assertThat(mapper.clampToUnitRange(0.0)).isEqualTo(0.0);
        assertThat(mapper.clampToUnitRange(0.75)).isEqualTo(0.75);
        assertThat(mapper.clampToUnitRange(1.0)).isEqualTo(1.0);
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private Chunk sampleChunk(ChunkId chunkId) {
        return Chunk.reconstitute(
                chunkId, documentId, tenantId, 0, "chunk content",
                ChunkMetadata.of("sliding-window"),
                "vector-id-001", "nomic-embed-text", 768,
                Instant.now(), Instant.now());
    }
}
