package com.mudassir.eka.infrastructure.retrieval.postgres;

import com.mudassir.eka.domain.chunk.ChunkId;
import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.domain.query.MetadataFilter;
import com.mudassir.eka.domain.retrieval.model.RetrievalOptions;
import com.mudassir.eka.domain.retrieval.model.RetrievalResult;
import com.mudassir.eka.domain.retrieval.model.RetrievedChunk;
import com.mudassir.eka.domain.retrieval.model.SearchMetadata;
import com.mudassir.eka.domain.retrieval.port.RetrievalPort;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.infrastructure.retrieval.postgres.Bm25MetadataFilterTranslator.FilterClause;
import com.mudassir.eka.infrastructure.retrieval.postgres.exception.Bm25RetrievalException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@link RetrievalPort} implementation backed by PostgreSQL Full-Text Search.
 *
 * <h3>Lexical ranking</h3>
 * <p>Uses {@code ts_rank(to_tsvector('english', content), websearch_to_tsquery('english', query))}
 * over the existing GIN index {@code idx_chunks_fts}. Results are ordered by descending rank
 * score before the {@code LIMIT} is applied, so the {@code rank} field on each
 * {@link RetrievedChunk} reflects the raw database ordering (0-based, non-consecutive after
 * minimumScore filtering — consistent with the {@link RetrievalPort} contract).
 *
 * <h3>Score normalization</h3>
 * <p>{@code ts_rank} scores are unbounded. This adapter applies
 * <strong>max-normalization</strong>: each score is divided by the maximum score
 * in the result set, mapping the highest-scoring result to {@code 1.0} and all others
 * proportionally below. See {@link Bm25ScoreNormalizer} for the full algorithm and
 * rationale. This is explicitly <em>not</em> a clamp — see
 * {@link com.mudassir.eka.infrastructure.retrieval.weaviate.RetrievedChunkMapper#clampToUnitRange}.
 *
 * <h3>Tenant isolation</h3>
 * <p>The SQL always includes {@code AND c.tenant_id = :tenantId}. This predicate is
 * part of the fixed query skeleton — it cannot be removed or overridden by the caller's
 * {@link MetadataFilter}. Cross-tenant data leakage is architecturally impossible.
 *
 * <h3>Stop-word queries</h3>
 * <p>{@code websearch_to_tsquery} silently discards stop words (e.g. "the", "is").
 * If all words in the query are stop words, the function returns an empty tsquery,
 * which matches nothing — the adapter returns an empty result rather than throwing.
 */
@Slf4j
@Qualifier("bm25Retrieval")
@Component
@RequiredArgsConstructor
public class PostgresBm25RetrievalAdapter implements RetrievalPort {

    private static final String STRATEGY = "bm25";

    /**
     * Fixed SQL skeleton. The tenant predicate and the FTS match predicate are
     * always present. Dynamic filter clauses from {@link Bm25MetadataFilterTranslator}
     * are appended before {@code ORDER BY}.
     *
     * <p>The {@code @@ websearch_to_tsquery} predicate activates the GIN index
     * {@code idx_chunks_fts}. The {@code ts_rank} call in SELECT reuses the same
     * tsvector, so PostgreSQL evaluates it once per row (not twice).
     *
     * <p>{@code d.deleted_at IS NULL} is required because the {@code @SQLRestriction}
     * on the JPA entity does not apply to native JDBC queries.
     */
    private static final String BASE_SQL = """
            SELECT
                c.id,
                c.document_id,
                c.tenant_id,
                c.content,
                ts_rank(to_tsvector('english', c.content),
                        websearch_to_tsquery('english', :queryText)) AS rank_score
            FROM chunks c
            JOIN documents d ON d.id = c.document_id
            WHERE
                c.tenant_id = :tenantId
                AND d.deleted_at IS NULL
                AND to_tsvector('english', c.content) @@ websearch_to_tsquery('english', :queryText)
            """;

    private static final String ORDER_AND_LIMIT = " ORDER BY rank_score DESC LIMIT :topK";

    private final NamedParameterJdbcTemplate   jdbcTemplate;
    private final Bm25MetadataFilterTranslator translator;

    @Override
    public RetrievalResult retrieve(
            String queryText,
            TenantId tenantId,
            MetadataFilter filter,
            RetrievalOptions options) {

        long startNano = System.nanoTime();

        FilterClause filterClause = translator.translate(filter);
        String sql = BASE_SQL + filterClause.sql() + ORDER_AND_LIMIT;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("queryText", queryText)
                .addValue("tenantId", tenantId.value())
                .addValue("topK", options.topK());
        params.addValues(filterClause.params());

        List<Bm25ResultRow> rows;
        try {
            rows = jdbcTemplate.query(sql, params, (rs, rowNum) -> new Bm25ResultRow(
                    rs.getObject("id", UUID.class),
                    rs.getObject("document_id", UUID.class),
                    rs.getObject("tenant_id", UUID.class),
                    rs.getString("content"),
                    rs.getDouble("rank_score")));
        } catch (Exception ex) {
            throw new Bm25RetrievalException(
                    "BM25 retrieval failed for tenant=" + tenantId, ex);
        }

        if (rows.isEmpty()) {
            long latencyMs = elapsedMs(startNano);
            log.debug("BM25 retrieval: tenant={} topK={} hits=0 latencyMs={}",
                    tenantId, options.topK(), latencyMs);
            return RetrievalResult.empty(STRATEGY, latencyMs);
        }

        double[] rawScores = rows.stream().mapToDouble(Bm25ResultRow::rawScore).toArray();
        double[] normalized = Bm25ScoreNormalizer.normalize(rawScores);

        List<RetrievedChunk> chunks = new ArrayList<>();
        for (int rawRank = 0; rawRank < rows.size(); rawRank++) {
            Bm25ResultRow row = rows.get(rawRank);
            double score = normalized[rawRank];
            if (score >= options.minimumScore()) {
                chunks.add(new RetrievedChunk(
                        ChunkId.of(row.chunkId()),
                        DocumentId.of(row.documentId()),
                        TenantId.of(row.tenantId()),
                        row.content(),
                        score,
                        rawRank));
            }
        }

        long latencyMs = elapsedMs(startNano);
        log.debug("BM25 retrieval: tenant={} topK={} hits={} latencyMs={}",
                tenantId, options.topK(), chunks.size(), latencyMs);

        return new RetrievalResult(chunks, new SearchMetadata(chunks.size(), latencyMs, STRATEGY));
    }

    private static long elapsedMs(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000L;
    }
}
