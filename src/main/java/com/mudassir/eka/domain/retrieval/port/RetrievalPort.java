package com.mudassir.eka.domain.retrieval.port;

import com.mudassir.eka.domain.query.MetadataFilter;
import com.mudassir.eka.domain.retrieval.model.RetrievalOptions;
import com.mudassir.eka.domain.retrieval.model.RetrievalResult;
import com.mudassir.eka.domain.shared.TenantId;

/**
 * Port for executing a retrieval query and returning ranked chunks.
 *
 * <h3>Score contract</h3>
 * <p>Every implementation must return {@link com.mudassir.eka.domain.retrieval.model.RetrievedChunk}
 * instances whose {@code score} field is in the range {@code [0.0, 1.0]}.
 * Adapters are responsible for normalizing their engine-native scores before
 * constructing results:
 * <ul>
 *   <li><b>Weaviate</b> — certainty is already {@code [0, 1]}; clamp defensively.</li>
 *   <li><b>BM25</b> — scores are unbounded; apply min-max or a monotonic transform.</li>
 *   <li><b>RRF</b> — reciprocal rank scores are in {@code (0, 1/k]}; scale to {@code [0, 1]}.</li>
 * </ul>
 *
 * <h3>Rank contract</h3>
 * <p>{@code RetrievedChunk.rank} must reflect the zero-based position of the item
 * in the <em>raw retrieval output</em> from the underlying engine, before any
 * post-filtering or re-ranking. Items that do not survive post-filtering are
 * excluded from the result; surviving items may therefore have non-consecutive
 * ranks. This invariant is required for correct Reciprocal Rank Fusion in P04.5.
 */
public interface RetrievalPort {

    RetrievalResult retrieve(
            String queryText,
            TenantId tenantId,
            MetadataFilter filter,
            RetrievalOptions options);
}
