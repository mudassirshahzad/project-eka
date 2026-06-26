package com.mudassir.eka.domain.query;

import java.util.UUID;

public record QueryId(UUID value) {

    public QueryId {
        if (value == null) throw new IllegalArgumentException("QueryId value must not be null");
    }

    public static QueryId generate() {
        return new QueryId(UUID.randomUUID());
    }

    public static QueryId of(UUID value) {
        return new QueryId(value);
    }

    public static QueryId of(String value) {
        return new QueryId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
