package com.mudassir.eka.infrastructure.vectorstore.weaviate.exception;

public class VectorSearchException extends VectorStoreException {

    public VectorSearchException(String message) {
        super(message);
    }

    public VectorSearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
