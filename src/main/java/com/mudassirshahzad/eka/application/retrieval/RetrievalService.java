package com.mudassirshahzad.eka.application.retrieval;

import com.mudassirshahzad.eka.domain.query.MetadataFilter;
import com.mudassirshahzad.eka.domain.retrieval.model.RetrievalOptions;
import com.mudassirshahzad.eka.domain.retrieval.model.RetrievalResult;
import com.mudassirshahzad.eka.domain.retrieval.port.QueryRewritePort;
import com.mudassirshahzad.eka.domain.retrieval.port.RankingPort;
import com.mudassirshahzad.eka.domain.retrieval.port.RetrievalPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Slf4j
@Service
public class RetrievalService {

    private static final int MAX_QUERY_LENGTH = 10_000;

    private final RetrievalPort    retrievalPort;
    private final RankingPort      rankingPort;
    private final QueryRewritePort queryRewritePort;

    public RetrievalService(
            RetrievalPort    retrievalPort,
            RankingPort      rankingPort,
            QueryRewritePort queryRewritePort) {
        this.retrievalPort    = Objects.requireNonNull(retrievalPort,    "retrievalPort must not be null");
        this.rankingPort      = Objects.requireNonNull(rankingPort,      "rankingPort must not be null");
        this.queryRewritePort = Objects.requireNonNull(queryRewritePort, "queryRewritePort must not be null");
    }

    public RetrievalResult retrieve(RetrievalRequest request) {
        validate(request);

        String           originalQuery = request.queryText();
        RetrievalOptions options       = request.options() != null ? request.options() : RetrievalOptions.DEFAULT;
        MetadataFilter   filter        = request.filter()  != null ? request.filter()  : MetadataFilter.NONE;

        String effectiveQuery = queryRewritePort.rewrite(originalQuery, request.tenantId());

        log.debug("Retrieving: tenant={} topK={} queryLength={} rewritten={}",
                request.tenantId(), options.topK(), originalQuery.length(),
                !effectiveQuery.equals(originalQuery));

        RetrievalResult raw = retrievalPort.retrieve(effectiveQuery, request.tenantId(), filter, options);

        RetrievalResult result = raw.hasResults()
                ? new RetrievalResult(rankingPort.rank(raw.items(), effectiveQuery), raw.metadata())
                : raw;

        log.debug("Retrieval complete: tenant={} hits={} latencyMs={}",
                request.tenantId(), result.metadata().totalHits(), result.metadata().latencyMs());

        return result;
    }

    private void validate(RetrievalRequest request) {
        Objects.requireNonNull(request,            "request must not be null");
        Objects.requireNonNull(request.tenantId(), "tenantId must not be null");

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
