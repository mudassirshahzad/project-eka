package com.mudassirshahzad.eka.infrastructure.llm.exception;

import com.mudassirshahzad.eka.domain.generation.exception.LlmException;

public class LlmInvalidResponseException extends LlmException {

    public LlmInvalidResponseException(String message) {
        super(message);
    }

    public LlmInvalidResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
