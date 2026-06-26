package com.mudassir.eka.domain.document;

public enum DocumentStatus {
    PENDING,
    PARSING,
    CHUNKING,
    EMBEDDING,
    INDEXED,
    FAILED;

    public boolean isTerminal() {
        return this == INDEXED || this == FAILED;
    }

    public boolean isProcessing() {
        return this == PARSING || this == CHUNKING || this == EMBEDDING;
    }
}
