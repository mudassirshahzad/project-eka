package com.mudassir.eka.infrastructure.ranking;

import com.mudassir.eka.domain.chunk.ChunkId;
import com.mudassir.eka.domain.retrieval.model.RetrievedChunk;
import com.mudassir.eka.domain.retrieval.port.RankingPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion (RRF) implementation of {@link RankingPort}.
 *
 * <h3>Algorithm</h3>
 * <p>For each unique chunk across all input occurrences, the RRF score is:
 * <pre>
 *   RRF Score = &Sigma; ( 1 / (k + rank_i) )
 * </pre>
 * where {@code rank_i} is the zero-based position of the chunk in retrieval
 * list {@code i} (as stored in {@link RetrievedChunk#rank()}, which records the
 * raw engine rank preserved by each retrieval adapter), and {@code k} is a
 * smoothing constant (default 60, per Cormack, Clarke &amp; Buettcher 2009).
 *
 * <h3>Input contract</h3>
 * <p>The flat {@code candidates} list is expected to be the concatenation of
 * multiple retrieval engine result lists. A chunk that was found by {@code N}
 * different engines will appear {@code N} times in the list, each occurrence
 * carrying the rank assigned by that engine. This is the calling convention
 * that the upcoming Hybrid Retrieval adapter (P04.5) will produce.
 *
 * <h3>Score normalization</h3>
 * <p>Raw RRF scores are max-normalized to {@code [0.0, 1.0]} before constructing
 * output {@link RetrievedChunk} objects:
 * <pre>
 *   normalized_i = rawRrfScore_i / max(rawRrfScore)
 * </pre>
 * The highest-scoring chunk always receives {@code 1.0}; others are scaled
 * proportionally. This satisfies the score contract from
 * {@link com.mudassir.eka.domain.retrieval.port.RetrievalPort} and preserves
 * relative RRF ordering. Note that the {@code score} field from input
 * {@link RetrievedChunk} objects (which carries per-adapter normalized scores)
 * is intentionally discarded — only the raw engine {@code rank} is used as RRF
 * formula input.
 *
 * <h3>Duplicate chunk handling</h3>
 * <p>If the same {@link ChunkId} appears multiple times in {@code candidates},
 * all occurrences are merged into a single output entry. RRF contributions from
 * every occurrence are summed (rewarding chunks that appear across multiple
 * retrieval strategies). The chunk metadata ({@code documentId}, {@code tenantId},
 * {@code content}) is taken from the first occurrence; all occurrences of the
 * same {@code ChunkId} are assumed to carry the same metadata.
 *
 * <h3>Output rank semantics</h3>
 * <p>The {@code rank} field in each output {@link RetrievedChunk} is the
 * zero-based position in the RRF-fused ranked list (0 = highest RRF score).
 * The original per-engine ranks are consumed as formula inputs and are not
 * preserved in the output — they have fulfilled their role.
 *
 * <h3>Tie-breaking</h3>
 * <p>When two chunks receive identical RRF scores, they are ordered by their
 * {@link ChunkId} UUID string representation in ascending lexicographic order.
 * This rule is deterministic and stable across JVM restarts and executions —
 * it does not depend on insertion order, input ordering, content, or thread
 * scheduling.
 *
 * <h3>Query text</h3>
 * <p>The {@code queryText} parameter is accepted to satisfy the {@link RankingPort}
 * interface contract (enabling future learning-to-rank or query-aware re-rankers
 * that need it) but is not used by the RRF algorithm itself.
 */
@Slf4j
@Component
public class RrfRankingAdapter implements RankingPort {

    /** Standard RRF smoothing constant from Cormack et al. 2009. */
    static final int DEFAULT_K = 60;

    private final int k;

    /**
     * @param k RRF smoothing constant; must be &ge; 1.
     *          Higher values reduce the advantage of top-ranked results;
     *          lower values amplify rank-position differences.
     */
    public RrfRankingAdapter(@Value("${app.retrieval.rrf-k:60}") int k) {
        if (k < 1) throw new IllegalArgumentException("k must be >= 1 but was " + k);
        this.k = k;
    }

    @Override
    public List<RetrievedChunk> rank(List<RetrievedChunk> candidates, String queryText) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        long startNano = System.nanoTime();

        // Accumulate RRF contributions per unique ChunkId.
        Map<ChunkId, RrfEntry> accumulators = new HashMap<>();
        for (RetrievedChunk chunk : candidates) {
            double contribution = 1.0 / ((double) k + chunk.rank());
            accumulators.merge(
                    chunk.chunkId(),
                    new RrfEntry(chunk, contribution),
                    (existing, incoming) -> existing.add(incoming.rrfScore()));
        }

        // Sort descending by RRF score; tie-break by ChunkId UUID ascending.
        List<RrfEntry> sorted = new ArrayList<>(accumulators.values());
        sorted.sort(Comparator.comparingDouble(RrfEntry::rrfScore).reversed()
                .thenComparing(e -> e.chunk().chunkId().value().toString()));

        // Max-normalize raw RRF scores to [0.0, 1.0].
        double maxScore = sorted.get(0).rrfScore();

        List<RetrievedChunk> result = new ArrayList<>(sorted.size());
        for (int rrfRank = 0; rrfRank < sorted.size(); rrfRank++) {
            RrfEntry       entry  = sorted.get(rrfRank);
            double         score  = maxScore > 0.0 ? entry.rrfScore() / maxScore : 0.0;
            RetrievedChunk source = entry.chunk();
            result.add(new RetrievedChunk(
                    source.chunkId(),
                    source.documentId(),
                    source.tenantId(),
                    source.content(),
                    score,
                    rrfRank));
        }

        log.debug("RRF ranking: inputSize={} merged={} k={} latencyMs={}",
                candidates.size(), result.size(), k,
                (System.nanoTime() - startNano) / 1_000_000L);

        return result;
    }

    /** Internal accumulator — never escapes this class. */
    private record RrfEntry(RetrievedChunk chunk, double rrfScore) {
        RrfEntry add(double additionalScore) {
            return new RrfEntry(chunk, rrfScore + additionalScore);
        }
    }
}
