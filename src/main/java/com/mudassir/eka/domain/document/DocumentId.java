package com.mudassir.eka.domain.document;

import java.util.UUID;

public record DocumentId(UUID value) {

    public DocumentId {
        if (value == null) throw new IllegalArgumentException("DocumentId value must not be null");
    }

    public static DocumentId generate() {
        return new DocumentId(UUID.randomUUID());
    }

    public static DocumentId of(UUID value) {
        return new DocumentId(value);
    }

    public static DocumentId of(String value) {
        return new DocumentId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
