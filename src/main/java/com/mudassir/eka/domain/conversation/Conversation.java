package com.mudassir.eka.domain.conversation;

import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Conversation {

    private final ConversationId  id;
    private final UserId          userId;
    private final TenantId        tenantId;
    private String                title;
    private final List<Message>   messages;
    private final Instant         createdAt;
    private Instant               updatedAt;
    private Instant               deletedAt;

    public static Conversation create(UserId userId, TenantId tenantId, String title) {
        Conversation c = new Conversation(ConversationId.generate(), userId, tenantId, Instant.now());
        c.title     = title;
        c.updatedAt = c.createdAt;
        return c;
    }

    public static Conversation reconstitute(
            ConversationId id, UserId userId, TenantId tenantId,
            String title, List<Message> messages,
            Instant createdAt, Instant updatedAt, Instant deletedAt
    ) {
        Conversation c = new Conversation(id, userId, tenantId, createdAt);
        c.title     = title;
        c.updatedAt = updatedAt;
        c.deletedAt = deletedAt;
        c.messages.addAll(messages);
        return c;
    }

    private Conversation(ConversationId id, UserId userId, TenantId tenantId, Instant createdAt) {
        this.id        = Objects.requireNonNull(id);
        this.userId    = Objects.requireNonNull(userId);
        this.tenantId  = Objects.requireNonNull(tenantId);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.messages  = new ArrayList<>();
    }

    public void addMessage(Message message) {
        messages.add(Objects.requireNonNull(message));
        this.updatedAt = Instant.now();
    }

    public void rename(String newTitle) {
        this.title     = newTitle;
        this.updatedAt = Instant.now();
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public List<Message> recentMessages(int windowSize) {
        int size  = messages.size();
        int start = Math.max(0, size - windowSize);
        return Collections.unmodifiableList(messages.subList(start, size));
    }

    public boolean isDeleted() { return deletedAt != null; }

    public ConversationId  getId()        { return id; }
    public UserId          getUserId()    { return userId; }
    public TenantId        getTenantId()  { return tenantId; }
    public String          getTitle()     { return title; }
    public List<Message>   getMessages()  { return Collections.unmodifiableList(messages); }
    public Instant         getCreatedAt() { return createdAt; }
    public Instant         getUpdatedAt() { return updatedAt; }
    public Instant         getDeletedAt() { return deletedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Conversation c)) return false;
        return id.equals(c.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
