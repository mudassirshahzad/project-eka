package com.mudassir.eka.domain.document;

import java.util.UUID;

public record DocumentTagId(UUID value) {

    public DocumentTagId {
        if (value == null) throw new IllegalArgumentException("DocumentTagId value must not be null");
    }

    public static DocumentTagId generate() {
        return new DocumentTagId(UUID.randomUUID());
    }

    public static DocumentTagId of(UUID value) {
        return new DocumentTagId(value);
    }

    public static DocumentTagId of(String value) {
        return new DocumentTagId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
