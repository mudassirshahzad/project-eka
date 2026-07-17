package com.mudassir.eka.infrastructure.prompt;

import com.mudassir.eka.domain.chunk.ChunkId;
import com.mudassir.eka.domain.conversation.Message;
import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.domain.generation.model.PromptBuildRequest;
import com.mudassir.eka.domain.generation.model.PromptRequest;
import com.mudassir.eka.domain.generation.model.ToolDefinition;
import com.mudassir.eka.domain.retrieval.model.AssembledChunk;
import com.mudassir.eka.domain.retrieval.model.AssembledContext;
import com.mudassir.eka.domain.shared.TenantId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;

import static com.mudassir.eka.infrastructure.prompt.TemplateBasedPromptBuilderAdapter.CONTEXT_PLACEHOLDER;
import static com.mudassir.eka.infrastructure.prompt.TemplateBasedPromptBuilderAdapter.EMPTY_CONTEXT_FALLBACK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TemplateBasedPromptBuilderAdapterTest {

    private static final String QUERY     = "What is retrieval augmented generation?";
    private static final int    BUDGET    = 4096;

    private final TenantId   tenantId = TenantId.generate();
    private final DocumentId docId    = DocumentId.generate();

    private static TemplateBasedPromptBuilderAdapter adapter;

    @BeforeAll
    static void loadAdapter() {
        adapter = new TemplateBasedPromptBuilderAdapter(
                new ClassPathResource("prompts/qa-system.txt"));
    }

    // ── Constructor / template loading ────────────────────────────────────────

    @Test
    void constructor_throwsWhenTemplateNotFound() throws Exception {
        Resource missing = mock(Resource.class);
        when(missing.getContentAsString(any())).thenThrow(new IOException("file not found"));
        when(missing.getDescription()).thenReturn("classpath:prompts/missing.txt");

        assertThatThrownBy(() -> new TemplateBasedPromptBuilderAdapter(missing))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Required prompt template not found");
    }

    @Test
    void constructor_loadsTemplateSuccessfully() {
        // If the adapter was constructed in @BeforeAll without throwing, the template loaded.
        assertThat(adapter).isNotNull();
    }

    // ── Null guard ────────────────────────────────────────────────────────────

    @Test
    void build_throwsOnNullRequest() {
        assertThatNullPointerException()
                .isThrownBy(() -> adapter.build(null))
                .withMessageContaining("request must not be null");
    }

    // ── User text — must be original query (ADR G02) ──────────────────────────

    @Test
    void build_userTextIsOriginalQuery() {
        PromptRequest result = adapter.build(request(emptyContext(), QUERY));

        assertThat(result.userText()).isEqualTo(QUERY);
    }

    @Test
    void build_userTextIsNotModified() {
        String verbatimQuery = "  How does BM25 rank documents? ";
        PromptRequest result = adapter.build(request(emptyContext(), verbatimQuery));

        assertThat(result.userText()).isEqualTo(verbatimQuery);
    }

    // ── Empty context ─────────────────────────────────────────────────────────

    @Test
    void build_emptyContext_fallbackAppearsInSystemText() {
        PromptRequest result = adapter.build(request(emptyContext(), QUERY));

        assertThat(result.systemText()).contains(EMPTY_CONTEXT_FALLBACK);
    }

    @Test
    void build_emptyContext_placeholderIsReplaced() {
        PromptRequest result = adapter.build(request(emptyContext(), QUERY));

        assertThat(result.systemText()).doesNotContain(CONTEXT_PLACEHOLDER);
    }

    // ── SOURCE markers (ADR G03) ──────────────────────────────────────────────

    @Test
    void build_singleChunk_sourceMarkerIsOne() {
        AssembledContext ctx = context(chunk("Some content about RAG.", 0));

        PromptRequest result = adapter.build(request(ctx, QUERY));

        assertThat(result.systemText()).contains("[SOURCE:1]");
    }

    @Test
    void build_multipleChunks_sourceMarkersAreSequential() {
        AssembledContext ctx = context(
                chunk("First chunk content.", 0),
                chunk("Second chunk content.", 1),
                chunk("Third chunk content.", 2));

        PromptRequest result = adapter.build(request(ctx, QUERY));

        assertThat(result.systemText())
                .contains("[SOURCE:1]")
                .contains("[SOURCE:2]")
                .contains("[SOURCE:3]");
    }

    @Test
    void build_fiveChunks_allSourceMarkersPresent() {
        AssembledContext ctx = context(
                chunk("c0", 0), chunk("c1", 1), chunk("c2", 2),
                chunk("c3", 3), chunk("c4", 4));

        PromptRequest result = adapter.build(request(ctx, QUERY));

        for (int i = 1; i <= 5; i++) {
            assertThat(result.systemText()).contains("[SOURCE:" + i + "]");
        }
    }

    // ── Ordering (ADR G03) ────────────────────────────────────────────────────

    @Test
    void build_preservesChunkOrderInSystemText() {
        AssembledContext ctx = context(
                chunk("alpha content", 0),
                chunk("beta content", 1),
                chunk("gamma content", 2));

        PromptRequest result = adapter.build(request(ctx, QUERY));

        int posAlpha = result.systemText().indexOf("alpha content");
        int posBeta  = result.systemText().indexOf("beta content");
        int posGamma = result.systemText().indexOf("gamma content");

        assertThat(posAlpha).isLessThan(posBeta);
        assertThat(posBeta).isLessThan(posGamma);
    }

    @Test
    void build_sourceMarkersAppearBeforeChunkContent() {
        AssembledContext ctx = context(chunk("unique chunk text", 0));

        PromptRequest result = adapter.build(request(ctx, QUERY));
        String system = result.systemText();

        assertThat(system.indexOf("[SOURCE:1]")).isLessThan(system.indexOf("unique chunk text"));
    }

    // ── Chunk separation ──────────────────────────────────────────────────────

    @Test
    void build_chunksAreSeparatedByDoubleNewline() {
        AssembledContext ctx = context(
                chunk("first", 0),
                chunk("second", 1));

        String rendered = TemplateBasedPromptBuilderAdapter.renderContext(ctx);

        assertThat(rendered).contains("[SOURCE:1]\nfirst\n\n[SOURCE:2]\nsecond");
    }

    // ── Chunk content preservation ────────────────────────────────────────────

    @Test
    void build_chunkContentAppearsVerbatimInSystemText() {
        String verbatim = "RAG combines retrieval with generation for grounded responses.";
        AssembledContext ctx = context(chunk(verbatim, 0));

        PromptRequest result = adapter.build(request(ctx, QUERY));

        assertThat(result.systemText()).contains(verbatim);
    }

    @Test
    void build_doesNotMergeOrRewriteChunks() {
        String contentA = "Chunk A: BM25 is a probabilistic retrieval model.";
        String contentB = "Chunk B: Vector search uses dense embeddings.";
        AssembledContext ctx = context(chunk(contentA, 0), chunk(contentB, 1));

        PromptRequest result = adapter.build(request(ctx, QUERY));

        assertThat(result.systemText())
                .contains(contentA)
                .contains(contentB);
    }

    // ── Memory messages (passthrough) ─────────────────────────────────────────

    @Test
    void build_memoryMessagesArePassedThrough() {
        Message prior = Message.userMessage("What is BM25?");
        var req = new PromptBuildRequest(
                emptyContext(), QUERY, List.of(prior), List.of(), tenantId);

        PromptRequest result = adapter.build(req);

        assertThat(result.memoryMessages()).containsExactly(prior);
    }

    @Test
    void build_emptyMemoryMessages_producesEmptyList() {
        PromptRequest result = adapter.build(request(emptyContext(), QUERY));

        assertThat(result.memoryMessages()).isEmpty();
    }

    // ── Tools (passthrough) ───────────────────────────────────────────────────

    @Test
    void build_toolsArePassedThrough() {
        ToolDefinition tool = new ToolDefinition("search", "web search");
        var req = new PromptBuildRequest(
                emptyContext(), QUERY, List.of(), List.of(tool), tenantId);

        PromptRequest result = adapter.build(req);

        assertThat(result.tools()).containsExactly(tool);
    }

    @Test
    void build_emptyTools_producesEmptyList() {
        PromptRequest result = adapter.build(request(emptyContext(), QUERY));

        assertThat(result.tools()).isEmpty();
    }

    // ── Result immutability ───────────────────────────────────────────────────

    @Test
    void build_resultMemoryMessages_areUnmodifiable() {
        Message prior = Message.userMessage("prior");
        var req = new PromptBuildRequest(
                emptyContext(), QUERY, List.of(prior), List.of(), tenantId);

        PromptRequest result = adapter.build(req);
        List<Message> messages = result.memoryMessages();

        assertThatThrownBy(() -> messages.add(Message.userMessage("inject")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void build_resultTools_areUnmodifiable() {
        ToolDefinition tool = new ToolDefinition("calc", "calculator");
        var req = new PromptBuildRequest(
                emptyContext(), QUERY, List.of(), List.of(tool), tenantId);

        PromptRequest result = adapter.build(req);
        List<ToolDefinition> tools = result.tools();

        assertThatThrownBy(() -> tools.add(new ToolDefinition("extra", "inject")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── Template placeholder replacement ─────────────────────────────────────

    @Test
    void build_templatePlaceholderIsAlwaysReplaced() {
        PromptRequest withContent  = adapter.build(request(context(chunk("text", 0)), QUERY));
        PromptRequest withoutContent = adapter.build(request(emptyContext(), QUERY));

        assertThat(withContent.systemText()).doesNotContain(CONTEXT_PLACEHOLDER);
        assertThat(withoutContent.systemText()).doesNotContain(CONTEXT_PLACEHOLDER);
    }

    // ── TenantId must not appear in prompt text (security) ───────────────────

    @Test
    void build_tenantIdIsNotLeakedIntoPromptText() {
        String tid = tenantId.toString();
        PromptRequest result = adapter.build(request(context(chunk("content", 0)), QUERY));

        assertThat(result.systemText()).doesNotContain(tid);
        assertThat(result.userText()).doesNotContain(tid);
    }

    // ── renderContext static helper ───────────────────────────────────────────

    @Test
    void renderContext_emptyContext_returnsFallback() {
        String rendered = TemplateBasedPromptBuilderAdapter.renderContext(emptyContext());

        assertThat(rendered).isEqualTo(EMPTY_CONTEXT_FALLBACK);
    }

    @Test
    void renderContext_singleChunk_noLeadingOrTrailingDoubleNewline() {
        String rendered = TemplateBasedPromptBuilderAdapter.renderContext(
                context(chunk("only chunk", 0)));

        assertThat(rendered).doesNotStartWith("\n\n");
        assertThat(rendered).doesNotEndWith("\n\n");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PromptBuildRequest request(AssembledContext ctx, String query) {
        return new PromptBuildRequest(ctx, query, List.of(), List.of(), tenantId);
    }

    private AssembledContext emptyContext() {
        return new AssembledContext(List.of(), QUERY, BUDGET, 0);
    }

    private AssembledContext context(AssembledChunk... chunks) {
        int estimated = chunks.length * 10;
        return new AssembledContext(List.of(chunks), QUERY, BUDGET, estimated);
    }

    private AssembledChunk chunk(String content, int position) {
        return new AssembledChunk(
                ChunkId.generate(), docId, tenantId, content, 0.9, position);
    }
}
