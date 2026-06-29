package com.mudassir.eka.infrastructure.retrieval.hybrid.exception;

public class HybridRetrievalException extends RuntimeException {

    public HybridRetrievalException(String message) {
        super(message);
    }

    public HybridRetrievalException(String message, Throwable cause) {
        super(message, cause);
    }
}
