package com.mudassirshahzad.eka.application.generation;

import com.mudassirshahzad.eka.domain.conversation.ConversationId;
import com.mudassirshahzad.eka.domain.generation.model.GenerationOptions;
import com.mudassirshahzad.eka.domain.retrieval.model.AssembledContext;
import com.mudassirshahzad.eka.domain.shared.TenantId;

import java.util.Objects;

/**
 * Application-layer request for text generation.
 *
 * <p>{@code conversationId} is optional (ADR M02). When present, {@link GenerationService}
 * fetches recent conversation history via {@link com.mudassirshahzad.eka.domain.generation.port.ConversationHistoryPort}
 * and injects it into the prompt as memory. When null, generation proceeds statelessly
 * with an empty memory window.
 *
 * @param assembledContext   ranked context chunks from retrieval; must not be null
 * @param originalQueryText  the user's verbatim question; must not be blank
 * @param tenantId           owning tenant; must not be null
 * @param options            generation parameters; null normalised to {@link GenerationOptions#DEFAULT}
 * @param conversationId     optional conversation for memory-augmented generation; null = stateless
 */
public record GenerationRequest(
        AssembledContext  assembledContext,
        String            originalQueryText,
        TenantId          tenantId,
        GenerationOptions options,
        ConversationId    conversationId
) {

    public GenerationRequest {
        Objects.requireNonNull(assembledContext,  "assembledContext must not be null");
        Objects.requireNonNull(originalQueryText, "originalQueryText must not be null");
        if (originalQueryText.isBlank())
            throw new IllegalArgumentException("originalQueryText must not be blank");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        options = options != null ? options : GenerationOptions.DEFAULT;
        // conversationId: null is valid — means stateless generation (ADR M02)
    }
}
