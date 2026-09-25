package com.mudassirshahzad.eka.application.reconciliation;

import com.mudassirshahzad.eka.application.document.DocumentIndexingService;
import com.mudassirshahzad.eka.application.document.EmbeddedChunk;
import com.mudassirshahzad.eka.application.document.EmbeddingService;
import com.mudassirshahzad.eka.domain.chunk.Chunk;
import com.mudassirshahzad.eka.domain.chunk.ChunkMetadata;
import com.mudassirshahzad.eka.domain.chunk.ChunkRepository;
import com.mudassirshahzad.eka.domain.document.DocumentId;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChunkReconciliationServiceTest {

    @Mock private ChunkRepository         chunkRepository;
    @Mock private EmbeddingService        embeddingService;
    @Mock private DocumentIndexingService indexingService;

    private SimpleMeterRegistry        meterRegistry;
    private ChunkReconciliationService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new ChunkReconciliationService(
                chunkRepository, embeddingService, indexingService, meterRegistry, 100);
    }

    private Chunk unindexedChunk() {
        return Chunk.create(DocumentId.generate(), TenantId.generate(), 0, "content",
                ChunkMetadata.of("sliding-window"));
    }

    @Test
    void noDrift_reportsNothingToDo_andNeverTouchesTheIngestionPath() {
        when(chunkRepository.findUnindexed(100)).thenReturn(List.of());

        ReconciliationResult result = service.reconcile();

        assertThat(result.detected()).isZero();
        assertThat(result.foundDrift()).isFalse();
        verify(embeddingService, never()).embed(anyList());
        verify(indexingService, never()).index(anyList());
    }

    @Test
    void unindexedChunks_areReEmbeddedAndReIndexed() {
        Chunk a = unindexedChunk();
        Chunk b = unindexedChunk();
        when(chunkRepository.findUnindexed(100)).thenReturn(List.of(a, b));
        when(embeddingService.embed(anyList())).thenReturn(List.of(new EmbeddedChunk(a, new float[]{0.1f})));

        ReconciliationResult result = service.reconcile();

        assertThat(result.detected()).isEqualTo(2);
        assertThat(result.repaired()).isEqualTo(2);
        assertThat(result.failed()).isZero();
        assertThat(result.hasUnrepairedDrift()).isFalse();
        // Repair reuses the real ingestion collaborators rather than a parallel implementation.
        verify(embeddingService, times(2)).embed(anyList());
        verify(indexingService, times(2)).index(anyList());
    }

    @Test
    void oneFailingChunk_doesNotPreventTheRestOfTheBatchFromBeingRepaired() {
        Chunk good1 = unindexedChunk();
        Chunk bad   = unindexedChunk();
        Chunk good2 = unindexedChunk();
        when(chunkRepository.findUnindexed(100)).thenReturn(List.of(good1, bad, good2));
        when(embeddingService.embed(List.of(bad))).thenThrow(new IllegalStateException("embedding down"));
        when(embeddingService.embed(List.of(good1))).thenReturn(List.of(new EmbeddedChunk(good1, new float[]{0.1f})));
        when(embeddingService.embed(List.of(good2))).thenReturn(List.of(new EmbeddedChunk(good2, new float[]{0.2f})));

        ReconciliationResult result = service.reconcile();

        assertThat(result.detected()).isEqualTo(3);
        assertThat(result.repaired()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.hasUnrepairedDrift()).isTrue();
    }

    @Test
    void driftAndRepairAreCounted_soAnOperatorCanAlertOnThem() {
        Chunk chunk = unindexedChunk();
        when(chunkRepository.findUnindexed(100)).thenReturn(List.of(chunk));
        when(embeddingService.embed(anyList())).thenReturn(List.of(new EmbeddedChunk(chunk, new float[]{0.1f})));

        service.reconcile();

        assertThat(meterRegistry.get("eka.reconciliation.drift.detected").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("eka.reconciliation.repaired").counter().count()).isEqualTo(1.0);
    }

    @Test
    void unrepairedDrift_incrementsItsOwnCounter_distinctFromDetection() {
        Chunk chunk = unindexedChunk();
        when(chunkRepository.findUnindexed(100)).thenReturn(List.of(chunk));
        when(embeddingService.embed(anyList())).thenThrow(new IllegalStateException("weaviate down"));

        service.reconcile();

        assertThat(meterRegistry.get("eka.reconciliation.failed").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("eka.reconciliation.repaired").counter().count()).isZero();
    }

    @Test
    void batchSizeBoundsHowMuchOnePassLoads() {
        ChunkReconciliationService bounded = new ChunkReconciliationService(
                chunkRepository, embeddingService, indexingService, meterRegistry, 7);
        when(chunkRepository.findUnindexed(7)).thenReturn(List.of());

        bounded.reconcile();

        verify(chunkRepository).findUnindexed(7);
    }

    @Test
    void constructor_rejectsANonPositiveBatchSize() {
        assertThatThrownBy(() -> new ChunkReconciliationService(
                chunkRepository, embeddingService, indexingService, meterRegistry, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("batch-size");
    }

    @Test
    void constructor_rejectsNullCollaborators() {
        assertThatThrownBy(() -> new ChunkReconciliationService(
                null, embeddingService, indexingService, meterRegistry, 100))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ChunkReconciliationService(
                chunkRepository, null, indexingService, meterRegistry, 100))
                .isInstanceOf(NullPointerException.class);
    }
}
