package com.mudassirshahzad.eka.application.reconciliation;

import com.mudassirshahzad.eka.application.document.DocumentIndexingService;
import com.mudassirshahzad.eka.application.document.EmbeddedChunk;
import com.mudassirshahzad.eka.application.document.EmbeddingService;
import com.mudassirshahzad.eka.domain.chunk.Chunk;
import com.mudassirshahzad.eka.domain.chunk.ChunkRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Detects and repairs Postgres↔Weaviate drift (WP-3, ADR OR02).
 *
 * <h3>What drift this finds</h3>
 * <p>Chunks committed to Postgres whose vectors never reached Weaviate. A chunk is assigned its
 * {@code vectorId} only after {@code VectorStore.index} succeeds, so a committed row with a null
 * {@code vectorId} is precisely the residue of a partial write. That state became reachable when
 * ADR HD01 split the upload pipeline's single long transaction into short per-step ones — the
 * correct fix for holding a database connection across a Weaviate call, but one that makes
 * "Postgres committed, Weaviate did not" a real outcome rather than an impossible one.
 *
 * <h3>Repair reuses the ingestion path rather than reimplementing it</h3>
 * <p>Repair is {@code EmbeddingService.embed} followed by {@code DocumentIndexingService.index} —
 * the same two collaborators a first-time ingestion uses, unchanged. {@code DocumentIndexingService}
 * is already idempotent (it clears any stale vector before re-indexing), so re-running it against a
 * chunk that was in fact indexed is harmless. Writing a second, reconciliation-specific indexing
 * path would create exactly the drift-between-two-implementations this service exists to detect.
 *
 * <h3>Deliberately not covered: Weaviate-side orphans</h3>
 * <p>The reverse direction — a vector in Weaviate with no surviving Postgres chunk — is not
 * detected here. Doing it properly needs a full-collection enumeration that {@code VectorStore}
 * does not expose, and the exposure is low: the delete path runs vector deletion first and is
 * naturally idempotent (ADR HD07), so a crash mid-delete leaves at worst an unreferenced vector
 * that no query can return, since every retrieval result is resolved back through Postgres.
 * Recorded as a bounded, deliberate limitation rather than left as an unstated gap.
 */
@Slf4j
@Service
public class ChunkReconciliationService {

    private final ChunkRepository         chunkRepository;
    private final EmbeddingService        embeddingService;
    private final DocumentIndexingService indexingService;
    private final MeterRegistry           meterRegistry;
    private final int                     batchSize;

    public ChunkReconciliationService(
            ChunkRepository chunkRepository,
            EmbeddingService embeddingService,
            DocumentIndexingService indexingService,
            MeterRegistry meterRegistry,
            @Value("${app.reconciliation.batch-size:100}") int batchSize
    ) {
        this.chunkRepository  = Objects.requireNonNull(chunkRepository, "chunkRepository");
        this.embeddingService = Objects.requireNonNull(embeddingService, "embeddingService");
        this.indexingService  = Objects.requireNonNull(indexingService, "indexingService");
        this.meterRegistry    = Objects.requireNonNull(meterRegistry, "meterRegistry");
        if (batchSize <= 0) {
            throw new IllegalStateException(
                    "app.reconciliation.batch-size must be positive, but was " + batchSize);
        }
        this.batchSize = batchSize;
    }

    /**
     * Runs one bounded reconciliation pass.
     *
     * <p>Deliberately not {@code @Transactional}: repair calls Ollama (embedding) and Weaviate
     * (indexing), and holding a pooled database connection across those is the exact anti-pattern
     * ADR HD01/HD07 removed from the ingestion and delete paths. The collaborators keep their own
     * short transactions.
     */
    public ReconciliationResult reconcile() {
        List<Chunk> unindexed = chunkRepository.findUnindexed(batchSize);
        if (unindexed.isEmpty()) {
            return ReconciliationResult.nothingToDo();
        }

        log.warn("Reconciliation detected {} chunk(s) present in Postgres but not indexed in Weaviate",
                unindexed.size());
        meterRegistry.counter("eka.reconciliation.drift.detected").increment(unindexed.size());

        int repaired = 0;
        int failed   = 0;

        // One chunk at a time: a single un-embeddable chunk must not prevent every other chunk in
        // the batch from being repaired. Throughput is not the constraint here — a reconciliation
        // pass is rare, and correctness of the surviving repairs matters more.
        for (Chunk chunk : unindexed) {
            try {
                List<EmbeddedChunk> embedded = embeddingService.embed(List.of(chunk));
                indexingService.index(embedded);
                repaired++;
            } catch (RuntimeException ex) {
                failed++;
                // Chunk content is never logged (Security/Logging Policy); the id is enough to act on.
                log.warn("Reconciliation could not repair chunk {}: {}",
                        chunk.getId(), ex.getClass().getSimpleName());
            }
        }

        meterRegistry.counter("eka.reconciliation.repaired").increment(repaired);
        if (failed > 0) {
            meterRegistry.counter("eka.reconciliation.failed").increment(failed);
            log.error("Reconciliation left {} chunk(s) unrepaired — a dependency is likely unavailable", failed);
        }

        log.info("Reconciliation pass complete: detected={} repaired={} failed={}",
                unindexed.size(), repaired, failed);
        return new ReconciliationResult(unindexed.size(), repaired, failed);
    }
}
