package com.mudassirshahzad.eka.infrastructure.llm.exception;

import com.mudassirshahzad.eka.domain.generation.exception.LlmException;

public class LlmProviderUnavailableException extends LlmException {

    public LlmProviderUnavailableException(String message) {
        super(message);
    }

    public LlmProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
