package com.mudassir.eka.domain.conversation;

import java.util.UUID;

public record ChatSessionId(UUID value) {

    public ChatSessionId {
        if (value == null) throw new IllegalArgumentException("ChatSessionId value must not be null");
    }

    public static ChatSessionId generate() {
        return new ChatSessionId(UUID.randomUUID());
    }

    public static ChatSessionId of(UUID value) {
        return new ChatSessionId(value);
    }

    public static ChatSessionId of(String value) {
        return new ChatSessionId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
