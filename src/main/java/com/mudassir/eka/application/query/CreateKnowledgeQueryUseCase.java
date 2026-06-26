package com.mudassir.eka.application.query;

import com.mudassir.eka.application.shared.ApplicationException;
import com.mudassir.eka.domain.query.KnowledgeQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CreateKnowledgeQueryUseCase {

    private static final int MAX_QUERY_TEXT_LENGTH = 10_000;

    private final QueryApplicationService queryService;

    public KnowledgeQuery execute(SubmitQueryCommand cmd) {
        Objects.requireNonNull(cmd, "command must not be null");
        Objects.requireNonNull(cmd.userId(), "userId must not be null");
        Objects.requireNonNull(cmd.tenantId(), "tenantId must not be null");
        Objects.requireNonNull(cmd.conversationId(), "conversationId must not be null");

        if (cmd.queryText() == null || cmd.queryText().isBlank()) {
            throw new ApplicationException("queryText must not be blank");
        }
        if (cmd.queryText().length() > MAX_QUERY_TEXT_LENGTH) {
            throw new ApplicationException(
                    "queryText exceeds maximum length of " + MAX_QUERY_TEXT_LENGTH + " characters");
        }

        log.debug("Creating knowledge query: user={} tenant={} conversation={}",
                cmd.userId(), cmd.tenantId(), cmd.conversationId());
        return queryService.submitQuery(cmd);
    }
}
