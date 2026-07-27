package com.mudassir.eka.infrastructure.llm.exception;

import com.mudassir.eka.domain.generation.exception.LlmException;

public class LlmProviderUnavailableException extends LlmException {

    public LlmProviderUnavailableException(String message) {
        super(message);
    }

    public LlmProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
