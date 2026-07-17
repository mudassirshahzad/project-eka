package com.mudassir.eka.domain.generation.model;

import com.mudassir.eka.domain.chunk.ChunkId;
import com.mudassir.eka.domain.conversation.Message;
import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.domain.retrieval.model.AssembledChunk;
import com.mudassir.eka.domain.retrieval.model.AssembledContext;
import com.mudassir.eka.domain.shared.TenantId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptBuildRequestTest {

    private final TenantId         tenantId = TenantId.generate();
    private final AssembledContext  emptyCtx = emptyContext();

    // ── Null guards ───────────────────────────────────────────────────────────

    @Test
    void constructor_throwsOnNullAssembledContext() {
        assertThatNullPointerException()
                .isThrownBy(() -> new PromptBuildRequest(null, "query", List.of(), List.of(), tenantId))
                .withMessageContaining("assembledContext must not be null");
    }

    @Test
    void constructor_throwsOnNullOriginalQueryText() {
        assertThatNullPointerException()
                .isThrownBy(() -> new PromptBuildRequest(emptyCtx, null, List.of(), List.of(), tenantId))
                .withMessageContaining("originalQueryText must not be null");
    }

    @Test
    void constructor_throwsOnNullTenantId() {
        assertThatNullPointerException()
                .isThrownBy(() -> new PromptBuildRequest(emptyCtx, "query", List.of(), List.of(), null))
                .withMessageContaining("tenantId must not be null");
    }

    // ── Null-tolerance for optional collections ───────────────────────────────

    @Test
    void constructor_acceptsNullMemoryMessages_defaultsToEmptyList() {
        var request = new PromptBuildRequest(emptyCtx, "query", null, List.of(), tenantId);

        assertThat(request.memoryMessages()).isEmpty();
    }

    @Test
    void constructor_acceptsNullTools_defaultsToEmptyList() {
        var request = new PromptBuildRequest(emptyCtx, "query", List.of(), null, tenantId);

        assertThat(request.tools()).isEmpty();
    }

    // ── Defensive copy / immutability ─────────────────────────────────────────

    @Test
    void memoryMessages_areDefensivelyCopied() {
        Message msg = Message.userMessage("hello");
        var mutable = new ArrayList<>(List.of(msg));
        var request = new PromptBuildRequest(emptyCtx, "query", mutable, List.of(), tenantId);

        mutable.add(Message.userMessage("injected after construction"));

        assertThat(request.memoryMessages()).hasSize(1);
    }

    @Test
    void tools_areDefensivelyCopied() {
        var tool    = new ToolDefinition("search", "web search");
        var mutable = new ArrayList<>(List.of(tool));
        var request = new PromptBuildRequest(emptyCtx, "query", List.of(), mutable, tenantId);

        mutable.add(new ToolDefinition("extra", "added later"));

        assertThat(request.tools()).hasSize(1);
    }

    @Test
    void memoryMessages_listIsUnmodifiable() {
        var request = new PromptBuildRequest(emptyCtx, "query",
                List.of(Message.userMessage("hi")), List.of(), tenantId);
        List<Message> messages = request.memoryMessages();

        assertThatThrownBy(() -> messages.add(Message.userMessage("fail")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void tools_listIsUnmodifiable() {
        var request = new PromptBuildRequest(emptyCtx, "query", List.of(),
                List.of(new ToolDefinition("t", "desc")), tenantId);
        List<ToolDefinition> tools = request.tools();

        assertThatThrownBy(() -> tools.add(new ToolDefinition("extra", "fail")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── Field access ──────────────────────────────────────────────────────────

    @Test
    void constructor_preservesAllFields() {
        Message      msg     = Message.userMessage("previous turn");
        ToolDefinition tool  = new ToolDefinition("search", "web");
        String       query   = "what is RAG?";

        var request = new PromptBuildRequest(emptyCtx, query, List.of(msg), List.of(tool), tenantId);

        assertThat(request.assembledContext()).isSameAs(emptyCtx);
        assertThat(request.originalQueryText()).isEqualTo(query);
        assertThat(request.memoryMessages()).containsExactly(msg);
        assertThat(request.tools()).containsExactly(tool);
        assertThat(request.tenantId()).isEqualTo(tenantId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static AssembledContext emptyContext() {
        return new AssembledContext(List.of(), "query", 4096, 0);
    }

    @SuppressWarnings("unused")
    private static AssembledChunk chunk(String content, int position) {
        return new AssembledChunk(
                ChunkId.generate(), DocumentId.generate(), TenantId.generate(),
                content, 0.9, position);
    }
}
