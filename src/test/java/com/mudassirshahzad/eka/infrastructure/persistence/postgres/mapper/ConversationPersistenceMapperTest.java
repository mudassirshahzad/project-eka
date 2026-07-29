package com.mudassirshahzad.eka.infrastructure.persistence.postgres.mapper;

import com.mudassirshahzad.eka.domain.conversation.Conversation;
import com.mudassirshahzad.eka.domain.conversation.Message;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity.AuditableEntity;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity.BaseUuidEntity;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity.ConversationEntity;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity.MessageEntity;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity.TenantEntity;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity.UserEntity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Baseline persistence-mapper coverage (P04.13.4). Pure unit tests — no Spring context, no
 * database — verifying the domain &lt;-&gt; entity translation boundary that the repository
 * adapter layer depends on, including the round trip now exercised end-to-end by
 * {@code PersistentConversationHistoryAdapter} (P04.13.2).
 *
 * <p>{@code createdAt}/{@code updatedAt} are normally populated by JPA's {@code @PrePersist}
 * lifecycle hook, which never fires when an entity is hand-built outside a real persistence
 * context — {@link #stampTimestamps(BaseUuidEntity)} simulates that hook via reflection so the
 * domain aggregates' non-null invariants are satisfiable in a pure unit test.
 */
class ConversationPersistenceMapperTest {

    private final ConversationPersistenceMapper mapper = new ConversationPersistenceMapper();

    // ── toDomain ──────────────────────────────────────────────────────────────

    @Test
    void toDomain_mapsAllScalarFields() {
        UserEntity   user   = userEntity();
        TenantEntity tenant = tenantEntity();
        ConversationEntity entity = conversationEntity(user, tenant, "My conversation");

        Conversation domain = mapper.toDomain(entity);

        assertThat(domain.getId().value()).isEqualTo(entity.getId());
        assertThat(domain.getUserId().value()).isEqualTo(user.getId());
        assertThat(domain.getTenantId().value()).isEqualTo(tenant.getId());
        assertThat(domain.getTitle()).isEqualTo("My conversation");
        assertThat(domain.isDeleted()).isFalse();
    }

    @Test
    void toDomain_mapsMessagesInEntityOrder() {
        ConversationEntity entity = conversationEntity(userEntity(), tenantEntity(), "chat");
        MessageEntity m1 = messageEntity(entity, "USER", "first");
        MessageEntity m2 = messageEntity(entity, "ASSISTANT", "second");
        entity.getMessages().add(m1);
        entity.getMessages().add(m2);

        Conversation domain = mapper.toDomain(entity);

        assertThat(domain.getMessages()).extracting(Message::content).containsExactly("first", "second");
    }

    @Test
    void toDomain_deletedConversation_isDeletedTrue() {
        ConversationEntity entity = conversationEntity(userEntity(), tenantEntity(), "chat");
        entity.setDeletedAt(Instant.now());

        Conversation domain = mapper.toDomain(entity);

        assertThat(domain.isDeleted()).isTrue();
    }

    // ── messageToDomain ───────────────────────────────────────────────────────

    @Test
    void messageToDomain_mapsRoleContentAndId() {
        ConversationEntity conversation = conversationEntity(userEntity(), tenantEntity(), "chat");
        MessageEntity entity = messageEntity(conversation, "USER", "hello");

        Message message = mapper.messageToDomain(entity);

        assertThat(message.id()).isEqualTo(entity.getId());
        assertThat(message.role().name()).isEqualTo("USER");
        assertThat(message.content()).isEqualTo("hello");
        assertThat(message.citations()).isEmpty();
    }

    // ── toEntity ──────────────────────────────────────────────────────────────

    @Test
    void toEntity_mapsDomainConversationToNewEntity() {
        UserEntity   user   = userEntity();
        TenantEntity tenant = tenantEntity();
        Conversation domain = Conversation.create(
                UserId.of(user.getId()), TenantId.of(tenant.getId()), "New chat");

        ConversationEntity entity = mapper.toEntity(domain, user, tenant);

        assertThat(entity.getId()).isEqualTo(domain.getId().value());
        assertThat(entity.getUser()).isSameAs(user);
        assertThat(entity.getTenant()).isSameAs(tenant);
        assertThat(entity.getTitle()).isEqualTo("New chat");
        assertThat(entity.getDeletedAt()).isNull();
    }

    // ── messageToEntity ───────────────────────────────────────────────────────

    @Test
    void messageToEntity_preservesIdRoleAndContent() {
        ConversationEntity conversationEntity = conversationEntity(userEntity(), tenantEntity(), "chat");
        Message domainMessage = Message.userMessage("What is RAG?");

        MessageEntity entity = mapper.messageToEntity(domainMessage, conversationEntity);

        assertThat(entity.getId()).isEqualTo(domainMessage.id());
        assertThat(entity.getRole()).isEqualTo("USER");
        assertThat(entity.getContent()).isEqualTo("What is RAG?");
        assertThat(entity.getConversation()).isSameAs(conversationEntity);
    }

    // ── Round trip ────────────────────────────────────────────────────────────

    @Test
    void toEntity_thenToDomain_isFieldEquivalentRoundTrip() {
        UserEntity   user   = userEntity();
        TenantEntity tenant = tenantEntity();
        Conversation original = Conversation.create(
                UserId.of(user.getId()), TenantId.of(tenant.getId()), "Round trip chat");
        original.addMessage(Message.userMessage("first turn"));

        ConversationEntity entity = mapper.toEntity(original, user, tenant);
        stampTimestamps(entity);
        entity.getMessages().add(mapper.messageToEntity(original.getMessages().get(0), entity));

        Conversation restored = mapper.toDomain(entity);

        assertThat(restored.getId()).isEqualTo(original.getId());
        assertThat(restored.getTitle()).isEqualTo(original.getTitle());
        assertThat(restored.getMessages()).extracting(Message::content)
                .containsExactly("first turn");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ConversationEntity conversationEntity(UserEntity user, TenantEntity tenant, String title) {
        ConversationEntity entity = ConversationEntity.builder()
                .user(user).tenant(tenant).title(title).build();
        entity.setId(UUID.randomUUID());
        stampTimestamps(entity);
        return entity;
    }

    private static UserEntity userEntity() {
        UserEntity user = UserEntity.builder()
                .tenant(tenantEntity())
                .email("user@example.com")
                .passwordHash("hash")
                .active(true)
                .build();
        user.setId(UUID.randomUUID());
        stampTimestamps(user);
        return user;
    }

    private static TenantEntity tenantEntity() {
        TenantEntity tenant = TenantEntity.builder()
                .name("Acme")
                .slug("acme-" + UUID.randomUUID())
                .active(true)
                .build();
        tenant.setId(UUID.randomUUID());
        stampTimestamps(tenant);
        return tenant;
    }

    private static MessageEntity messageEntity(ConversationEntity conversation, String role, String content) {
        MessageEntity entity = MessageEntity.builder()
                .conversation(conversation)
                .role(role)
                .content(content)
                .build();
        entity.setId(UUID.randomUUID());
        stampTimestamps(entity);
        return entity;
    }

    /**
     * Simulates JPA's {@code @PrePersist} lifecycle hook, which is what normally populates
     * {@code createdAt} (and {@code updatedAt} for {@link AuditableEntity} subtypes) — never
     * invoked when an entity is constructed outside a real {@code EntityManager}.
     */
    private static void stampTimestamps(BaseUuidEntity entity) {
        setField(entity, BaseUuidEntity.class, "createdAt", Instant.now());
        if (entity instanceof AuditableEntity auditable) {
            auditable.setUpdatedAt(Instant.now());
        }
    }

    private static void setField(Object target, Class<?> declaringClass, String fieldName, Object value) {
        try {
            Field field = declaringClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
