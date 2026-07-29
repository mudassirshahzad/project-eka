package com.mudassirshahzad.eka.infrastructure.persistence.postgres.adapter;

import com.mudassirshahzad.eka.domain.conversation.Conversation;
import com.mudassirshahzad.eka.domain.conversation.ConversationId;
import com.mudassirshahzad.eka.domain.conversation.Message;
import com.mudassirshahzad.eka.domain.generation.port.ConversationHistoryPort;
import com.mudassirshahzad.eka.domain.shared.PageRequest;
import com.mudassirshahzad.eka.domain.shared.PageResult;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
import com.mudassirshahzad.eka.infrastructure.conversation.PersistentConversationHistoryAdapter;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity.TenantEntity;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity.UserEntity;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.mapper.ConversationPersistenceMapper;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.repository.TenantJpaRepository;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.repository.UserJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Baseline enterprise-quality integration test (P04.13.4) for the persistence layer, run against
 * a real Postgres via the project's existing {@code jdbc:tc:postgresql:...} Testcontainers
 * profile (already configured in {@code application.yml}'s {@code test} profile — no new test
 * infrastructure introduced).
 *
 * <p>This class also doubles as the end-to-end proof for the P04.13.2 conversation-memory
 * reconciliation: {@link #conversationHistory_reflectsMessagesWrittenThroughRepository()} writes
 * through {@link ConversationRepositoryAdapter} exactly as {@code ConversationApplicationService}
 * does, then reads back through {@link PersistentConversationHistoryAdapter} exactly as
 * {@code GenerationService} does — against a real database, not mocks.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ConversationRepositoryAdapter.class, ConversationPersistenceMapper.class})
class ConversationRepositoryAdapterIT {

    @Autowired private ConversationRepositoryAdapter conversationRepository;
    @Autowired private UserJpaRepository             userJpaRepository;
    @Autowired private TenantJpaRepository           tenantJpaRepository;
    @Autowired private TestEntityManager             entityManager;

    // ── Round trip ────────────────────────────────────────────────────────────

    @Test
    void save_thenFindById_roundTripsConversationAndMessages() {
        TenantEntity tenant = persistTenant();
        UserEntity   user   = persistUser(tenant);

        Conversation conversation = Conversation.create(
                UserId.of(user.getId()), TenantId.of(tenant.getId()), "My first chat");
        conversation.addMessage(Message.userMessage("What is RAG?"));
        conversation.addMessage(Message.assistantMessage("Retrieval augmented generation.", List.of(), null));

        conversationRepository.save(conversation);
        entityManager.flush();
        entityManager.clear();

        Optional<Conversation> found = conversationRepository.findById(conversation.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("My first chat");
        assertThat(found.get().getMessages()).extracting(Message::content)
                .containsExactly("What is RAG?", "Retrieval augmented generation.");
    }

    // ── Incremental message append (mirrors ConversationApplicationService.addUserMessage) ────

    @Test
    void save_calledTwice_appendsOnlyNewMessages() {
        TenantEntity tenant = persistTenant();
        UserEntity   user   = persistUser(tenant);

        Conversation conversation = Conversation.create(
                UserId.of(user.getId()), TenantId.of(tenant.getId()), "Growing chat");
        conversation.addMessage(Message.userMessage("first"));
        conversationRepository.save(conversation);
        entityManager.flush();
        entityManager.clear();

        Conversation reloaded = conversationRepository.findById(conversation.getId()).orElseThrow();
        reloaded.addMessage(Message.userMessage("second"));
        conversationRepository.save(reloaded);
        entityManager.flush();
        entityManager.clear();

        Conversation finalState = conversationRepository.findById(conversation.getId()).orElseThrow();
        assertThat(finalState.getMessages()).extracting(Message::content)
                .containsExactly("first", "second");
    }

    // ── Soft delete ───────────────────────────────────────────────────────────

    @Test
    void softDelete_excludesConversationFromFindById() {
        TenantEntity tenant = persistTenant();
        UserEntity   user   = persistUser(tenant);
        Conversation conversation = Conversation.create(
                UserId.of(user.getId()), TenantId.of(tenant.getId()), "Doomed chat");
        conversationRepository.save(conversation);
        entityManager.flush();

        conversationRepository.softDelete(conversation.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(conversationRepository.findById(conversation.getId())).isEmpty();
    }

    // ── Tenant isolation ──────────────────────────────────────────────────────

    @Test
    void findByUserIdAndTenantId_doesNotReturnConversationsFromAnotherTenant() {
        TenantEntity tenantA = persistTenant();
        TenantEntity tenantB = persistTenant();
        UserEntity   userA   = persistUser(tenantA);
        UserEntity   userB   = persistUser(tenantB);

        Conversation conversationA = Conversation.create(
                UserId.of(userA.getId()), TenantId.of(tenantA.getId()), "Tenant A's chat");
        conversationRepository.save(conversationA);
        entityManager.flush();
        entityManager.clear();

        PageResult<Conversation> tenantBView = conversationRepository.findByUserIdAndTenantId(
                UserId.of(userB.getId()), TenantId.of(tenantB.getId()), new PageRequest(0, 10));

        assertThat(tenantBView.content()).isEmpty();
    }

    // ── End-to-end conversation memory reconciliation proof (P04.13.2) ─────────

    @Test
    void conversationHistory_reflectsMessagesWrittenThroughRepository() {
        TenantEntity tenant = persistTenant();
        UserEntity   user   = persistUser(tenant);
        TenantId     tenantId = TenantId.of(tenant.getId());

        // Write path: exactly what ConversationApplicationService.addUserMessage() does.
        Conversation conversation = Conversation.create(UserId.of(user.getId()), tenantId, "Real chat");
        conversation.addMessage(Message.userMessage("What is RAG?"));
        conversationRepository.save(conversation);
        entityManager.flush();
        entityManager.clear();

        // Read path: exactly what GenerationService.fetchHistory() does.
        ConversationHistoryPort historyPort = new PersistentConversationHistoryAdapter(conversationRepository);
        List<Message> history = historyPort.getRecentMessages(conversation.getId(), tenantId, 10);

        assertThat(history).extracting(Message::content).containsExactly("What is RAG?");
    }

    @Test
    void conversationHistory_wrongTenant_returnsEmptyNotAnotherTenantsMessages() {
        TenantEntity ownerTenant   = persistTenant();
        TenantEntity foreignTenant = persistTenant();
        UserEntity   user          = persistUser(ownerTenant);

        Conversation conversation = Conversation.create(
                UserId.of(user.getId()), TenantId.of(ownerTenant.getId()), "Private chat");
        conversation.addMessage(Message.userMessage("secret"));
        conversationRepository.save(conversation);
        entityManager.flush();
        entityManager.clear();

        ConversationHistoryPort historyPort = new PersistentConversationHistoryAdapter(conversationRepository);
        List<Message> history = historyPort.getRecentMessages(
                conversation.getId(), TenantId.of(foreignTenant.getId()), 10);

        assertThat(history).isEmpty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private TenantEntity persistTenant() {
        TenantEntity tenant = TenantEntity.builder()
                .name("Acme")
                .slug("acme-" + UUID.randomUUID())
                .active(true)
                .build();
        tenant.setId(UUID.randomUUID());
        return tenantJpaRepository.save(tenant);
    }

    private UserEntity persistUser(TenantEntity tenant) {
        UserEntity user = UserEntity.builder()
                .tenant(tenant)
                .email(UUID.randomUUID() + "@example.com")
                .passwordHash("hash")
                .active(true)
                .build();
        user.setId(UUID.randomUUID());
        return userJpaRepository.save(user);
    }
}
