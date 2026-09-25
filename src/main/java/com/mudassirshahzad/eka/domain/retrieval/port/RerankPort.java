package com.mudassirshahzad.eka.domain.retrieval.port;

import com.mudassirshahzad.eka.domain.retrieval.model.RetrievedChunk;

import java.util.List;

/**
 * Re-orders already-retrieved candidates by how well each one actually answers the query
 * (WP-4, ADR RQ01).
 *
 * <h3>Why this is a separate port from {@link RankingPort}</h3>
 * <p>{@link RankingPort} fuses several engines' result lists using rank positions alone — it never
 * looks at chunk content, which is exactly why Reciprocal Rank Fusion works across engines whose
 * scores are not comparable. Re-ranking is the opposite: it reads the query and the chunk text
 * together and judges relevance. Collapsing both into one port would force every implementation to
 * satisfy two unrelated contracts, and would make "fuse, then judge" impossible to express as a
 * sequence.
 *
 * <h3>Implementations must degrade, never fail</h3>
 * <p>Re-ranking is a quality improvement layered on top of a result set that is already correct and
 * already authorized. An implementation that cannot score (model unavailable, unparseable output)
 * must return the candidates in their incoming order rather than throw — the same "best-effort
 * enrichment must never fail the request" contract {@code CitationPort} carries (ADR C04).
 */
public interface RerankPort {

    /**
     * @param queryText  the effective query text retrieval actually used
     * @param candidates already-fused, already-authorized candidates, best-first
     * @param topN       maximum results to return; implementations must not return more
     * @return re-ordered candidates, never null, never larger than {@code topN}
     */
    List<RetrievedChunk> rerank(String queryText, List<RetrievedChunk> candidates, int topN);
}
