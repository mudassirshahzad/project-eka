package com.mudassirshahzad.eka.infrastructure.llm.exception;

import com.mudassirshahzad.eka.domain.generation.exception.LlmException;

public class LlmTimeoutException extends LlmException {

    public LlmTimeoutException(String message) {
        super(message);
    }

    public LlmTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
