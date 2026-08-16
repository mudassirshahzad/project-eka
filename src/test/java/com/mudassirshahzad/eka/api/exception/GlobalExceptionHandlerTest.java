package com.mudassirshahzad.eka.api.exception;

import com.mudassirshahzad.eka.application.retrieval.InvalidRetrievalRequestException;
import com.mudassirshahzad.eka.application.retrieval.RetrievalException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P05.5, ADR HD04: {@link InvalidRetrievalRequestException} must resolve to 400, not the 502 its
 * {@link RetrievalException} supertype gets — proven directly against the handler methods, since
 * that is exactly where the dispatch-order guarantee (more specific handler wins) lives.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleInvalidRetrievalRequest_returnsBadRequest() {
        ProblemDetail problem = handler.handleInvalidRetrievalRequest(
                new InvalidRetrievalRequestException("queryText exceeds maximum length"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getDetail()).isEqualTo("queryText exceeds maximum length");
    }

    @Test
    void handleUpstreamFailure_stillReturnsBadGatewayForPlainRetrievalException() {
        ProblemDetail problem = handler.handleUpstreamFailure(
                new RetrievalException("Weaviate unreachable"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
    }
}
