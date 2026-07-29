package com.mudassirshahzad.eka.infrastructure.llm.exception;

import com.mudassirshahzad.eka.domain.generation.exception.LlmException;

public class LlmRateLimitException extends LlmException {

    public LlmRateLimitException(String message) {
        super(message);
    }

    public LlmRateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
