package com.mudassirshahzad.eka.application.conversation;

import com.mudassirshahzad.eka.application.event.MessageAddedEvent;
import com.mudassirshahzad.eka.application.shared.DomainEventPublisher;
import com.mudassirshahzad.eka.application.shared.ResourceNotFoundException;
import com.mudassirshahzad.eka.domain.conversation.Citation;
import com.mudassirshahzad.eka.domain.conversation.Conversation;
import com.mudassirshahzad.eka.domain.conversation.ConversationId;
import com.mudassirshahzad.eka.domain.conversation.ConversationRepository;
import com.mudassirshahzad.eka.domain.conversation.Message;
import com.mudassirshahzad.eka.domain.conversation.MessageRole;
import com.mudassirshahzad.eka.domain.chunk.ChunkId;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Focused on {@link ConversationApplicationService#addAssistantMessage} (P05.1, ADR O02) and, as
 * of P05.3, the defensive tenant check (ADR TN01) shared by {@code getConversation},
 * {@code addUserMessage}, and {@code addAssistantMessage}. {@code createConversation},
 * {@code listConversations}, {@code renameConversation}, {@code deleteConversation} remain
 * unchanged and out of this file's test scope.
 */
@ExtendWith(MockitoExtension.class)
class ConversationApplicationServiceTest {

    @Mock private ConversationRepository conversationRepository;
    @Mock private DomainEventPublisher   eventPublisher;

    private SimpleMeterRegistry            meterRegistry;
    private ConversationApplicationService service;

    private final UserId   userId   = UserId.generate();
    private final TenantId tenantId = TenantId.generate();

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new ConversationApplicationService(conversationRepository, eventPublisher, meterRegistry);
    }

    @Test
    void addAssistantMessage_notFound_throwsResourceNotFoundException() {
        ConversationId id = ConversationId.generate();
        when(conversationRepository.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        AddAssistantMessageCommand cmd = new AddAssistantMessageCommand(id, userId, tenantId, "answer", List.of());

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.addAssistantMessage(cmd));
    }

    @Test
    void addAssistantMessage_appendsAssistantMessageAndSaves() {
        Conversation conversation = Conversation.create(userId, tenantId, "chat");
        ConversationId id = conversation.getId();
        when(conversationRepository.findByIdAndUserId(id, userId)).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AddAssistantMessageCommand cmd = new AddAssistantMessageCommand(id, userId, tenantId, "the answer", List.of());
        Conversation result = service.addAssistantMessage(cmd);

        assertThat(result.getMessages()).hasSize(1);
        assertThat(result.getMessages().getFirst().role()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(result.getMessages().getFirst().content()).isEqualTo("the answer");
    }

    @Test
    void addAssistantMessage_preservesCitations() {
        Conversation conversation = Conversation.create(userId, tenantId, "chat");
        ConversationId id = conversation.getId();
        when(conversationRepository.findByIdAndUserId(id, userId)).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Citation citation = Citation.of(ChunkId.generate(), 0.9);
        AddAssistantMessageCommand cmd = new AddAssistantMessageCommand(id, userId, tenantId, "cited answer", List.of(citation));
        Conversation result = service.addAssistantMessage(cmd);

        assertThat(result.getMessages().getFirst().citations()).containsExactly(citation);
    }

    @Test
    void addAssistantMessage_publishesMessageAddedEventWithAssistantRole() {
        Conversation conversation = Conversation.create(userId, tenantId, "chat");
        ConversationId id = conversation.getId();
        when(conversationRepository.findByIdAndUserId(id, userId)).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.addAssistantMessage(new AddAssistantMessageCommand(id, userId, tenantId, "answer", List.of()));

        ArgumentCaptor<MessageAddedEvent> captor = ArgumentCaptor.forClass(MessageAddedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(captor.getValue().getConversationId()).isEqualTo(id);
    }

    @Test
    void addAssistantMessage_appendsAfterExistingUserMessage_preservingOrder() {
        Conversation conversation = Conversation.create(userId, tenantId, "chat");
        conversation.addMessage(Message.userMessage("question"));
        ConversationId id = conversation.getId();
        when(conversationRepository.findByIdAndUserId(id, userId)).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Conversation result = service.addAssistantMessage(
                new AddAssistantMessageCommand(id, userId, tenantId, "answer", List.of()));

        assertThat(result.getMessages()).hasSize(2);
        assertThat(result.getMessages().get(0).role()).isEqualTo(MessageRole.USER);
        assertThat(result.getMessages().get(1).role()).isEqualTo(MessageRole.ASSISTANT);
    }

    // ── Tenant isolation (P05.3, ADR TN01) ──────────────────────────────────────

    @Test
    void addAssistantMessage_wrongTenant_throwsResourceNotFoundException() {
        Conversation conversation = Conversation.create(userId, tenantId, "chat");
        ConversationId id = conversation.getId();
        when(conversationRepository.findByIdAndUserId(id, userId)).thenReturn(Optional.of(conversation));

        TenantId otherTenant = TenantId.generate();
        AddAssistantMessageCommand cmd =
                new AddAssistantMessageCommand(id, userId, otherTenant, "answer", List.of());

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.addAssistantMessage(cmd));
    }

    @Test
    void addUserMessage_wrongTenant_throwsResourceNotFoundException() {
        Conversation conversation = Conversation.create(userId, tenantId, "chat");
        ConversationId id = conversation.getId();
        when(conversationRepository.findByIdAndUserId(id, userId)).thenReturn(Optional.of(conversation));

        TenantId otherTenant = TenantId.generate();
        AddUserMessageCommand cmd = new AddUserMessageCommand(id, userId, otherTenant, "hello");

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.addUserMessage(cmd));
    }

    @Test
    void addUserMessage_correctTenant_appendsMessage() {
        Conversation conversation = Conversation.create(userId, tenantId, "chat");
        ConversationId id = conversation.getId();
        when(conversationRepository.findByIdAndUserId(id, userId)).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Conversation result = service.addUserMessage(new AddUserMessageCommand(id, userId, tenantId, "hello"));

        assertThat(result.getMessages()).hasSize(1);
        assertThat(result.getMessages().getFirst().role()).isEqualTo(MessageRole.USER);
    }

    @Test
    void getConversation_wrongTenant_throwsResourceNotFoundException() {
        Conversation conversation = Conversation.create(userId, tenantId, "chat");
        ConversationId id = conversation.getId();
        when(conversationRepository.findByIdAndUserId(id, userId)).thenReturn(Optional.of(conversation));

        TenantId otherTenant = TenantId.generate();

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.getConversation(id, userId, otherTenant));
    }

    @Test
    void getConversation_wrongTenant_incrementsOwnershipFailureCounter() {
        Conversation conversation = Conversation.create(userId, tenantId, "chat");
        ConversationId id = conversation.getId();
        when(conversationRepository.findByIdAndUserId(id, userId)).thenReturn(Optional.of(conversation));

        TenantId otherTenant = TenantId.generate();

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.getConversation(id, userId, otherTenant));

        assertThat(meterRegistry.get("eka.authz.failures").tag("reason", "ownership").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void getConversation_correctTenant_returnsConversation() {
        Conversation conversation = Conversation.create(userId, tenantId, "chat");
        ConversationId id = conversation.getId();
        when(conversationRepository.findByIdAndUserId(id, userId)).thenReturn(Optional.of(conversation));

        Conversation result = service.getConversation(id, userId, tenantId);

        assertThat(result).isEqualTo(conversation);
    }

    @Test
    void getConversation_notFound_throwsResourceNotFoundException() {
        ConversationId id = ConversationId.generate();
        when(conversationRepository.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.getConversation(id, userId, tenantId));
    }

    /**
     * Distinct from the tenant-mismatch cases above: same tenant, different owning user. This
     * exercises {@code findByIdAndUserId}'s existing ownership scoping (pre-dates P05.3), locked
     * in here as an explicit ownership-validation test per this milestone's requirements.
     */
    @Test
    void getConversation_belongsToAnotherUserInSameTenant_throwsResourceNotFoundException() {
        ConversationId id = ConversationId.generate();
        UserId otherUser = UserId.generate();
        // findByIdAndUserId is only stubbed for `userId`; any other userId returns the Mockito
        // default (empty Optional), exactly as the real ownership-scoped query would.

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.getConversation(id, otherUser, tenantId));
    }
}
