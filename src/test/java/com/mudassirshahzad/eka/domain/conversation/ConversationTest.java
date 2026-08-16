package com.mudassirshahzad.eka.domain.conversation;

import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * v0.6.1, ADR EX08: the title invariant previously lived only in a REST DTO and in a
 * {@code CreateConversationUseCase} the controller never actually called — moved here so it is
 * enforced regardless of caller, per DDD's "always-valid aggregate" principle.
 */
class ConversationTest {

    private final UserId   userId   = UserId.generate();
    private final TenantId tenantId = TenantId.generate();

    @Test
    void create_blankTitle_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Conversation.create(userId, tenantId, "   "))
                .withMessageContaining("blank");
    }

    @Test
    void create_nullTitle_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Conversation.create(userId, tenantId, null))
                .withMessageContaining("blank");
    }

    @Test
    void create_titleExceedingMaxLength_throws() {
        String tooLong = "A".repeat(Conversation.MAX_TITLE_LENGTH + 1);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Conversation.create(userId, tenantId, tooLong))
                .withMessageContaining("500");
    }

    @Test
    void create_titleAtMaxLength_succeeds() {
        String maxTitle = "A".repeat(Conversation.MAX_TITLE_LENGTH);
        Conversation conversation = Conversation.create(userId, tenantId, maxTitle);

        assertThat(conversation.getTitle()).isEqualTo(maxTitle);
    }

    @Test
    void rename_blankTitle_throws() {
        Conversation conversation = Conversation.create(userId, tenantId, "original");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> conversation.rename(""));
    }

    @Test
    void rename_validTitle_updatesTitle() {
        Conversation conversation = Conversation.create(userId, tenantId, "original");
        conversation.rename("renamed");

        assertThat(conversation.getTitle()).isEqualTo("renamed");
    }

    /**
     * Reconstitution must never re-validate: tightening this rule in the future must not break
     * loading of rows a prior, looser rule already accepted as valid at write time.
     */
    @Test
    void reconstitute_titleViolatingCurrentRule_doesNotThrow() {
        String tooLongForCreate = "A".repeat(Conversation.MAX_TITLE_LENGTH + 1);
        Instant now = Instant.now();

        Conversation conversation = Conversation.reconstitute(
                ConversationId.generate(), userId, tenantId,
                tooLongForCreate, List.of(), now, now, null);

        assertThat(conversation.getTitle()).isEqualTo(tooLongForCreate);
    }
}
