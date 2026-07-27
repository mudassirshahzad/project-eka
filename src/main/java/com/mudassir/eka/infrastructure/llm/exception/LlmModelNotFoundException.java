package com.mudassir.eka.infrastructure.llm.exception;

import com.mudassir.eka.domain.generation.exception.LlmException;

public class LlmModelNotFoundException extends LlmException {

    public LlmModelNotFoundException(String message) {
        super(message);
    }
}
