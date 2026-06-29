package com.mudassir.eka.application.retrieval;

import com.mudassir.eka.domain.query.MetadataFilter;
import com.mudassir.eka.domain.retrieval.model.RetrievalOptions;
import com.mudassir.eka.domain.retrieval.model.RetrievalResult;
import com.mudassir.eka.domain.retrieval.port.RankingPort;
import com.mudassir.eka.domain.retrieval.port.RetrievalPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Slf4j
@Service
public class RetrievalService {

    private static final int MAX_QUERY_LENGTH = 10_000;

    private final RetrievalPort retrievalPort;
    private final RankingPort   rankingPort;

    public RetrievalService(RetrievalPort retrievalPort, RankingPort rankingPort) {
        this.retrievalPort = Objects.requireNonNull(retrievalPort, "retrievalPort must not be null");
        this.rankingPort   = Objects.requireNonNull(rankingPort,   "rankingPort must not be null");
    }

    public RetrievalResult retrieve(RetrievalRequest request) {
        validate(request);

        String           queryText = request.queryText();
        RetrievalOptions options   = request.options() != null ? request.options() : RetrievalOptions.DEFAULT;
        MetadataFilter   filter    = request.filter()  != null ? request.filter()  : MetadataFilter.NONE;

        log.debug("Retrieving: tenant={} topK={} queryLength={}",
                request.tenantId(), options.topK(), queryText.length());

        RetrievalResult raw = retrievalPort.retrieve(queryText, request.tenantId(), filter, options);

        RetrievalResult result = raw.hasResults()
                ? new RetrievalResult(rankingPort.rank(raw.items(), queryText), raw.metadata())
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
