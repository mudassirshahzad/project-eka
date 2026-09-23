package com.mudassirshahzad.eka.application.retrieval;

import com.mudassirshahzad.eka.domain.document.ClassificationPolicyPort;
import com.mudassirshahzad.eka.domain.document.Document;
import com.mudassirshahzad.eka.domain.document.DocumentId;
import com.mudassirshahzad.eka.domain.document.DocumentRepository;
import com.mudassirshahzad.eka.domain.query.MetadataFilter;
import com.mudassirshahzad.eka.domain.retrieval.model.RetrievalOptions;
import com.mudassirshahzad.eka.domain.retrieval.model.RetrievalResult;
import com.mudassirshahzad.eka.domain.retrieval.model.RetrievedChunk;
import com.mudassirshahzad.eka.domain.retrieval.model.SearchMetadata;
import com.mudassirshahzad.eka.domain.retrieval.port.QueryRewritePort;
import com.mudassirshahzad.eka.domain.retrieval.port.RankingPort;
import com.mudassirshahzad.eka.domain.retrieval.port.RetrievalPort;
import com.mudassirshahzad.eka.domain.user.UserRole;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * {@code retrieve()} runs inside an {@code eka.retrieval} {@link Observation} (P05.4, ADR OB02) —
 * a Micrometer timer today, and trace-ready with zero code changes the moment distributed tracing
 * (out of this milestone's scope) is added.
 *
 * <p>Failures from {@link QueryRewritePort} and {@link RetrievalPort} — e.g. Weaviate/Postgres
 * being unreachable — are caught and rewrapped as {@link RetrievalException} (P05.5, ADR HD04),
 * so {@code GlobalExceptionHandler}'s existing 502 mapping actually applies to them. Without this,
 * an infrastructure-level exception type (e.g. {@code HybridRetrievalException}, which extends
 * plain {@code RuntimeException}, not {@code RetrievalException}) would fall through to the
 * generic 500 handler instead — a genuine upstream failure misreported as an unexplained server
 * error. {@link InvalidRetrievalRequestException} (already a {@code RetrievalException}) is
 * rethrown unchanged — it is a 400-shaped client error, not an upstream failure.
 *
 * <p><b>Authorization Filter (P06.2):</b> runs between {@link QueryRewritePort} and
 * {@link RetrievalPort} in spirit — in code, as a post-fetch pass over whatever
 * {@link RetrievalPort} returned, before ranking. It is deliberately <em>not</em> pushed down into
 * either engine's native filter translation: {@code HybridRetrievalAdapter} shares one
 * {@link MetadataFilter} across both the Weaviate and BM25 adapters, and Weaviate indexes no
 * per-chunk classification property — a criterion the BM25 side could honor but the Weaviate side
 * would silently mis-resolve as "matches nothing" would zero out vector search results the moment
 * it was populated. Filtering here instead, keyed off {@link RetrievedChunk#documentId()} (present
 * on every result regardless of which engine produced it), is engine-agnostic and correct for
 * Hybrid, vector-only, and BM25-only alike. A denied chunk is silently dropped, never a distinct
 * error — indistinguishable from "didn't match the query" (ADR OW01/A06's anti-enumeration
 * precedent, extended to classification).
 */
@Slf4j
@Service
public class RetrievalService {

    private static final int MAX_QUERY_LENGTH = 10_000;

    private final RetrievalPort            retrievalPort;
    private final RankingPort              rankingPort;
    private final QueryRewritePort         queryRewritePort;
    private final DocumentRepository       documentRepository;
    private final ClassificationPolicyPort classificationPolicyPort;
    private final ObservationRegistry      observationRegistry;

    public RetrievalService(
            RetrievalPort            retrievalPort,
            RankingPort              rankingPort,
            QueryRewritePort         queryRewritePort,
            DocumentRepository       documentRepository,
            ClassificationPolicyPort classificationPolicyPort,
            ObservationRegistry      observationRegistry) {
        this.retrievalPort            = Objects.requireNonNull(retrievalPort,            "retrievalPort must not be null");
        this.rankingPort              = Objects.requireNonNull(rankingPort,              "rankingPort must not be null");
        this.queryRewritePort         = Objects.requireNonNull(queryRewritePort,         "queryRewritePort must not be null");
        this.documentRepository       = Objects.requireNonNull(documentRepository,       "documentRepository must not be null");
        this.classificationPolicyPort = Objects.requireNonNull(classificationPolicyPort, "classificationPolicyPort must not be null");
        this.observationRegistry      = Objects.requireNonNull(observationRegistry,      "observationRegistry must not be null");
    }

    public RetrievalResult retrieve(RetrievalRequest request) {
        return Observation.createNotStarted("eka.retrieval", observationRegistry)
                .observe(() -> doRetrieve(request));
    }

    private RetrievalResult doRetrieve(RetrievalRequest request) {
        validate(request);

        String           originalQuery = request.queryText();
        RetrievalOptions options       = request.options() != null ? request.options() : RetrievalOptions.DEFAULT;
        MetadataFilter   filter        = request.filter()  != null ? request.filter()  : MetadataFilter.NONE;

        try {
            String effectiveQuery = queryRewritePort.rewrite(originalQuery, request.tenantId());

            log.debug("Retrieving: tenant={} topK={} queryLength={} rewritten={}",
                    request.tenantId(), options.topK(), originalQuery.length(),
                    !effectiveQuery.equals(originalQuery));

            RetrievalResult raw = retrievalPort.retrieve(effectiveQuery, request.tenantId(), filter, options);

            List<RetrievedChunk> authorized = applyClassificationFilter(raw.items(), request.roles());

            RetrievalResult result = !authorized.isEmpty()
                    ? new RetrievalResult(rankingPort.rank(authorized, effectiveQuery),
                            authorizedMetadata(raw.metadata(), authorized.size()), effectiveQuery)
                    : RetrievalResult.empty(raw.metadata().strategy(), raw.metadata().latencyMs(), effectiveQuery);

            log.debug("Retrieval complete: tenant={} hits={} latencyMs={}",
                    request.tenantId(), result.metadata().totalHits(), result.metadata().latencyMs());

            return result;
        } catch (RetrievalException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new RetrievalException("Retrieval failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Drops every chunk whose owning document the caller's roles aren't cleared to see. Resolves
     * classification for every distinct {@link RetrievedChunk#documentId()} in a single batch
     * lookup ({@link DocumentRepository#findByIds}) rather than one query per chunk. A document
     * that no longer exists (deleted between indexing and this query) resolves to "unknown
     * classification" — fail-closed, same as a genuinely unclassified document.
     */
    private List<RetrievedChunk> applyClassificationFilter(List<RetrievedChunk> chunks, Set<UserRole> roles) {
        if (chunks.isEmpty()) {
            return chunks;
        }

        List<DocumentId> documentIds = chunks.stream()
                .map(RetrievedChunk::documentId)
                .distinct()
                .toList();

        Map<DocumentId, String> classificationByDocument = new HashMap<>();
        for (Document document : documentRepository.findByIds(documentIds)) {
            classificationByDocument.put(document.getId(), classificationName(document));
        }

        return chunks.stream()
                .filter(chunk -> classificationPolicyPort.isPermitted(
                        roles, classificationByDocument.get(chunk.documentId())))
                .toList();
    }

    private SearchMetadata authorizedMetadata(SearchMetadata original, int authorizedCount) {
        return new SearchMetadata(authorizedCount, original.latencyMs(), original.strategy());
    }

    private static String classificationName(Document document) {
        return document.getMetadata() != null && document.getMetadata().classification() != null
                ? document.getMetadata().classification().name()
                : null;
    }

    private void validate(RetrievalRequest request) {
        Objects.requireNonNull(request,            "request must not be null");
        Objects.requireNonNull(request.tenantId(), "tenantId must not be null");
        Objects.requireNonNull(request.roles(),    "roles must not be null");

        String queryText = request.queryText();
        if (queryText == null || queryText.isBlank()) {
            throw new InvalidRetrievalRequestException("queryText must not be blank");
        }
        if (queryText.length() > MAX_QUERY_LENGTH) {
            throw new InvalidRetrievalRequestException(
                    "queryText exceeds maximum length of " + MAX_QUERY_LENGTH + " characters");
        }
    }
}
