package com.mudassir.eka.domain.generation.model;

import com.mudassir.eka.domain.conversation.Message;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptRequestTest {

    private static final String SYSTEM = "You are a helpful assistant.\n\nContext:\nsome context";
    private static final String USER   = "What is retrieval augmented generation?";

    // ── Null guards ───────────────────────────────────────────────────────────

    @Test
    void constructor_throwsOnNullSystemText() {
        assertThatNullPointerException()
                .isThrownBy(() -> new PromptRequest(null, USER, List.of(), List.of()))
                .withMessageContaining("systemText must not be null");
    }

    @Test
    void constructor_throwsOnNullUserText() {
        assertThatNullPointerException()
                .isThrownBy(() -> new PromptRequest(SYSTEM, null, List.of(), List.of()))
                .withMessageContaining("userText must not be null");
    }

    // ── Null-tolerance for optional collections ───────────────────────────────

    @Test
    void constructor_acceptsNullMemoryMessages_defaultsToEmptyList() {
        var req = new PromptRequest(SYSTEM, USER, null, List.of());

        assertThat(req.memoryMessages()).isEmpty();
    }

    @Test
    void constructor_acceptsNullTools_defaultsToEmptyList() {
        var req = new PromptRequest(SYSTEM, USER, List.of(), null);

        assertThat(req.tools()).isEmpty();
    }

    // ── Defensive copy / immutability ─────────────────────────────────────────

    @Test
    void memoryMessages_areDefensivelyCopied() {
        Message msg     = Message.userMessage("turn one");
        var mutable     = new ArrayList<>(List.of(msg));
        var req         = new PromptRequest(SYSTEM, USER, mutable, List.of());

        mutable.add(Message.userMessage("injected after construction"));

        assertThat(req.memoryMessages()).hasSize(1);
    }

    @Test
    void tools_areDefensivelyCopied() {
        var tool    = new ToolDefinition("search", "web search");
        var mutable = new ArrayList<>(List.of(tool));
        var req     = new PromptRequest(SYSTEM, USER, List.of(), mutable);

        mutable.add(new ToolDefinition("extra", "added later"));

        assertThat(req.tools()).hasSize(1);
    }

    @Test
    void memoryMessages_listIsUnmodifiable() {
        var req = new PromptRequest(SYSTEM, USER, List.of(Message.userMessage("hi")), List.of());
        List<Message> messages = req.memoryMessages();

        assertThatThrownBy(() -> messages.add(Message.userMessage("fail")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void tools_listIsUnmodifiable() {
        var req = new PromptRequest(SYSTEM, USER, List.of(),
                List.of(new ToolDefinition("t", "desc")));
        List<ToolDefinition> tools = req.tools();

        assertThatThrownBy(() -> tools.add(new ToolDefinition("fail", "fail")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── Field access ──────────────────────────────────────────────────────────

    @Test
    void constructor_preservesAllFields() {
        Message        msg  = Message.userMessage("prior turn");
        ToolDefinition tool = new ToolDefinition("calculator", "math tool");

        var req = new PromptRequest(SYSTEM, USER, List.of(msg), List.of(tool));

        assertThat(req.systemText()).isEqualTo(SYSTEM);
        assertThat(req.userText()).isEqualTo(USER);
        assertThat(req.memoryMessages()).containsExactly(msg);
        assertThat(req.tools()).containsExactly(tool);
    }
}
