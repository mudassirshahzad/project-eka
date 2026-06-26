package com.mudassir.eka.domain.shared;

import java.util.UUID;

public record TenantId(UUID value) {

    public TenantId {
        if (value == null) throw new IllegalArgumentException("TenantId value must not be null");
    }

    public static TenantId generate() {
        return new TenantId(UUID.randomUUID());
    }

    public static TenantId of(UUID value) {
        return new TenantId(value);
    }

    public static TenantId of(String value) {
        return new TenantId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
