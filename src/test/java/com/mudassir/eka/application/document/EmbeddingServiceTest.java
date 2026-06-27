package com.mudassir.eka.application.document;

import com.mudassir.eka.domain.chunk.Chunk;
import com.mudassir.eka.domain.chunk.ChunkMetadata;
import com.mudassir.eka.domain.chunk.EmbeddingProvider;
import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.domain.shared.TenantId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatRuntimeException;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    @Mock private EmbeddingProvider embeddingProvider;

    private EmbeddingService service(int batchSize) {
        return new EmbeddingService(embeddingProvider, batchSize);
    }

    private Chunk makeChunk(String content) {
        ChunkMetadata meta = ChunkMetadata.of("sliding-window");
        return Chunk.create(DocumentId.generate(), TenantId.generate(), 0, content, meta);
    }

    @Test
    void embed_returnsEmptyList_forEmptyInput() {
        assertThat(service(32).embed(List.of())).isEmpty();
    }

    @Test
    void embed_assignsProvenanceToEachChunk() {
        float[] vector = {0.1f, 0.2f, 0.3f};
        when(embeddingProvider.embed(anyList())).thenReturn(List.of(vector));
        when(embeddingProvider.modelName()).thenReturn("nomic-embed-text");
        when(embeddingProvider.dimension()).thenReturn(3);

        Chunk chunk = makeChunk("hello world");
        List<EmbeddedChunk> results = service(32).embed(List.of(chunk));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).embedding()).isSameAs(vector);
        assertThat(chunk.isEmbedded()).isTrue();
        assertThat(chunk.getEmbeddingModel()).isEqualTo("nomic-embed-text");
        assertThat(chunk.getEmbeddingDimension()).isEqualTo(3);
        assertThat(chunk.getEmbeddedAt()).isNotNull();
    }

    @Test
    void embed_callsProviderInBatches() {
        float[] v = {1.0f};
        when(embeddingProvider.embed(anyList())).thenReturn(List.of(v, v));
        when(embeddingProvider.modelName()).thenReturn("model");
        when(embeddingProvider.dimension()).thenReturn(1);

        // batchSize=2, 4 chunks → 2 calls
        List<Chunk> chunks = List.of(
                makeChunk("a"), makeChunk("b"), makeChunk("c"), makeChunk("d"));
        service(2).embed(chunks);

        verify(embeddingProvider, times(2)).embed(anyList());
    }

    @Test
    void embed_batchTextsMatchChunkContent() {
        float[] v = {0.5f};
        when(embeddingProvider.modelName()).thenReturn("model");
        when(embeddingProvider.dimension()).thenReturn(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        when(embeddingProvider.embed(captor.capture())).thenReturn(List.of(v, v));

        List<Chunk> chunks = List.of(makeChunk("foo"), makeChunk("bar"));
        service(32).embed(chunks);

        assertThat(captor.getValue()).containsExactly("foo", "bar");
    }

    @Test
    void embed_retriesOnFailureAndSucceedsOnThirdAttempt() {
        float[] v = {1.0f};
        when(embeddingProvider.modelName()).thenReturn("model");
        when(embeddingProvider.dimension()).thenReturn(1);
        when(embeddingProvider.embed(anyList()))
                .thenThrow(new RuntimeException("timeout"))
                .thenThrow(new RuntimeException("timeout"))
                .thenReturn(List.of(v));

        List<EmbeddedChunk> results = service(32).embed(List.of(makeChunk("retry me")));

        assertThat(results).hasSize(1);
        verify(embeddingProvider, times(3)).embed(anyList());
    }

    @Test
    void embed_throwsAfterMaxRetries() {
        when(embeddingProvider.embed(anyList()))
                .thenThrow(new RuntimeException("permanent failure"));

        assertThatRuntimeException()
                .isThrownBy(() -> service(32).embed(List.of(makeChunk("fail"))))
                .withMessageContaining("Embedding failed after");
    }
}
