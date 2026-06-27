package com.mudassir.eka.application.document;

import com.mudassir.eka.domain.chunk.Chunk;
import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.domain.document.ParsedDocument;
import com.mudassir.eka.domain.document.ParsedMetadata;
import com.mudassir.eka.domain.document.ParsingStatus;
import com.mudassir.eka.domain.document.SupportedFormat;
import com.mudassir.eka.domain.shared.TenantId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChunkingServiceTest {

    @Mock  private ChunkingStrategy  chunkingStrategy;
    @InjectMocks private ChunkingService service;

    private final DocumentId documentId = DocumentId.generate();
    private final TenantId   tenantId   = TenantId.generate();

    private ParsedDocument parsed(String text) {
        return new ParsedDocument(text,
                new ParsedMetadata(null, null, null, 0, text.length()),
                SupportedFormat.TXT, ParsingStatus.SUCCESS, Instant.now());
    }

    @Test
    void chunk_returnsEmptyList_whenStrategyProducesNoSegments() {
        when(chunkingStrategy.chunk(anyString())).thenReturn(List.of());

        List<Chunk> result = service.chunk(parsed("hello"), documentId, tenantId);

        assertThat(result).isEmpty();
    }

    @Test
    void chunk_createsChunksWithCorrectDocumentAndTenant() {
        when(chunkingStrategy.name()).thenReturn("sliding-window");
        when(chunkingStrategy.chunk(anyString())).thenReturn(List.of(
                new TextSegment("first chunk", 0, 11, 2, 0),
                new TextSegment("second chunk", 5, 17, 2, 1)
        ));

        List<Chunk> chunks = service.chunk(parsed("first chunk second chunk"), documentId, tenantId);

        assertThat(chunks).hasSize(2);
        chunks.forEach(c -> {
            assertThat(c.getDocumentId()).isEqualTo(documentId);
            assertThat(c.getTenantId()).isEqualTo(tenantId);
        });
    }

    @Test
    void chunk_setsChunkMetadataFromSegmentOffsets() {
        when(chunkingStrategy.name()).thenReturn("sliding-window");
        when(chunkingStrategy.chunk(anyString())).thenReturn(List.of(
                new TextSegment("hello world", 0, 11, 2, 0)
        ));

        List<Chunk> chunks = service.chunk(parsed("hello world"), documentId, tenantId);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getMetadata().startOffset()).isEqualTo(0);
        assertThat(chunks.get(0).getMetadata().endOffset()).isEqualTo(11);
        assertThat(chunks.get(0).getMetadata().tokenCount()).isEqualTo(2);
    }

    @Test
    void chunk_setsChunkingStrategyNameInMetadata() {
        when(chunkingStrategy.name()).thenReturn("sliding-window");
        when(chunkingStrategy.chunk(anyString())).thenReturn(List.of(
                new TextSegment("text", 0, 4, 1, 0)
        ));

        List<Chunk> chunks = service.chunk(parsed("text"), documentId, tenantId);

        assertThat(chunks.get(0).getMetadata().chunkingStrategy()).isEqualTo("sliding-window");
    }

    @Test
    void chunk_sequenceNumbersMatchSegmentOrder() {
        when(chunkingStrategy.name()).thenReturn("sliding-window");
        when(chunkingStrategy.chunk(anyString())).thenReturn(List.of(
                new TextSegment("a", 0, 1, 1, 0),
                new TextSegment("b", 0, 1, 1, 1),
                new TextSegment("c", 0, 1, 1, 2)
        ));

        List<Chunk> chunks = service.chunk(parsed("a b c"), documentId, tenantId);

        assertThat(chunks).hasSize(3);
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).getSequenceNumber()).isEqualTo(i);
        }
    }
}
