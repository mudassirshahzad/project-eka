package com.mudassir.eka.domain.conversation;

import java.util.UUID;

public record ConversationId(UUID value) {

    public ConversationId {
        if (value == null) throw new IllegalArgumentException("ConversationId value must not be null");
    }

    public static ConversationId generate() {
        return new ConversationId(UUID.randomUUID());
    }

    public static ConversationId of(UUID value) {
        return new ConversationId(value);
    }

    public static ConversationId of(String value) {
        return new ConversationId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
