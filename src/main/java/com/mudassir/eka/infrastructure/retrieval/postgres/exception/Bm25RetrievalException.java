package com.mudassir.eka.infrastructure.retrieval.postgres.exception;

public class Bm25RetrievalException extends RuntimeException {

    public Bm25RetrievalException(String message, Throwable cause) {
        super(message, cause);
    }
}
