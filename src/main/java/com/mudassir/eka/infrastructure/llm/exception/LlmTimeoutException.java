package com.mudassir.eka.infrastructure.llm.exception;

import com.mudassir.eka.domain.generation.exception.LlmException;

public class LlmTimeoutException extends LlmException {

    public LlmTimeoutException(String message) {
        super(message);
    }

    public LlmTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
