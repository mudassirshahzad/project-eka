package com.mudassirshahzad.eka.infrastructure.llm.exception;

import com.mudassirshahzad.eka.domain.generation.exception.LlmException;

public class LlmModelNotFoundException extends LlmException {

    public LlmModelNotFoundException(String message) {
        super(message);
    }
}
