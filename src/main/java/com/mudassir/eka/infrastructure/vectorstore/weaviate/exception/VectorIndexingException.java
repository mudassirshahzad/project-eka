package com.mudassir.eka.infrastructure.vectorstore.weaviate.exception;

public class VectorIndexingException extends VectorStoreException {

    public VectorIndexingException(String message) {
        super(message);
    }

    public VectorIndexingException(String message, Throwable cause) {
        super(message, cause);
    }
}
