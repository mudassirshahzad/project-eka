package com.mudassirshahzad.eka.application.retrieval;

import com.mudassirshahzad.eka.domain.chunk.ChunkId;
import com.mudassirshahzad.eka.domain.document.ClassificationPolicyPort;
import com.mudassirshahzad.eka.domain.document.Document;
import com.mudassirshahzad.eka.domain.document.DocumentClassification;
import com.mudassirshahzad.eka.domain.document.DocumentId;
import com.mudassirshahzad.eka.domain.document.DocumentMetadata;
import com.mudassirshahzad.eka.domain.document.DocumentRepository;
import com.mudassirshahzad.eka.domain.document.SupportedFormat;
import com.mudassirshahzad.eka.domain.query.MetadataFilter;
import com.mudassirshahzad.eka.domain.retrieval.model.RetrievalOptions;
import com.mudassirshahzad.eka.domain.retrieval.model.RetrievalResult;
import com.mudassirshahzad.eka.domain.retrieval.model.RetrievedChunk;
import com.mudassirshahzad.eka.domain.retrieval.model.SearchMetadata;
import com.mudassirshahzad.eka.domain.retrieval.port.QueryRewritePort;
import com.mudassirshahzad.eka.domain.retrieval.port.RankingPort;
import com.mudassirshahzad.eka.domain.retrieval.port.RetrievalPort;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
import com.mudassirshahzad.eka.domain.user.UserRole;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetrievalServiceTest {

    @Mock private RetrievalPort            retrievalPort;
    @Mock private RankingPort              rankingPort;
    @Mock private QueryRewritePort         queryRewritePort;
    @Mock private DocumentRepository       documentRepository;
    @Mock private ClassificationPolicyPort classificationPolicyPort;

    private RetrievalService service;

    private final TenantId      tenantId = TenantId.generate();
    private final UserId        userId   = UserId.generate();
    private final Set<UserRole> roles    = Set.of(UserRole.ADMIN);

    @BeforeEach
    void setUp() {
        service = new RetrievalService(retrievalPort, rankingPort, queryRewritePort,
                documentRepository, classificationPolicyPort, ObservationRegistry.NOOP);
        // Pass-through by default; tests that need a different return value override this.
        // Lenient to avoid UnnecessaryStubbingException on validation-failure tests.
        lenient().when(queryRewritePort.rewrite(anyString(), any()))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(documentRepository.findByIds(any())).thenReturn(List.of());
        lenient().when(classificationPolicyPort.isPermitted(any(), any())).thenReturn(true);
    }

    // ── Constructor guards ────────────────────────────────────────────────────

    @Test
    void constructor_rejectsNullRetrievalPort() {
        assertThatNullPointerException()
                .isThrownBy(() -> new RetrievalService(null, rankingPort, queryRewritePort,
                        documentRepository, classificationPolicyPort, ObservationRegistry.NOOP))
                .withMessageContaining("retrievalPort");
    }

    @Test
    void constructor_rejectsNullRankingPort() {
        assertThatNullPointerException()
                .isThrownBy(() -> new RetrievalService(retrievalPort, null, queryRewritePort,
                        documentRepository, classificationPolicyPort, ObservationRegistry.NOOP))
                .withMessageContaining("rankingPort");
    }

    @Test
    void constructor_rejectsNullQueryRewritePort() {
        assertThatNullPointerException()
                .isThrownBy(() -> new RetrievalService(retrievalPort, rankingPort, null,
                        documentRepository, classificationPolicyPort, ObservationRegistry.NOOP))
                .withMessageContaining("queryRewritePort");
    }

    @Test
    void constructor_rejectsNullDocumentRepository() {
        assertThatNullPointerException()
                .isThrownBy(() -> new RetrievalService(retrievalPort, rankingPort, queryRewritePort,
                        null, classificationPolicyPort, ObservationRegistry.NOOP))
                .withMessageContaining("documentRepository");
    }

    @Test
    void constructor_rejectsNullClassificationPolicyPort() {
        assertThatNullPointerException()
                .isThrownBy(() -> new RetrievalService(retrievalPort, rankingPort, queryRewritePort,
                        documentRepository, null, ObservationRegistry.NOOP))
                .withMessageContaining("classificationPolicyPort");
    }

    @Test
    void constructor_rejectsNullObservationRegistry() {
        assertThatNullPointerException()
                .isThrownBy(() -> new RetrievalService(retrievalPort, rankingPort, queryRewritePort,
                        documentRepository, classificationPolicyPort, null))
                .withMessageContaining("observationRegistry");
    }

    // ── Null guards on retrieve() ─────────────────────────────────────────────

    @Test
    void retrieve_rejectsNullRequest() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.retrieve(null));
    }

    @Test
    void retrieve_rejectsNullTenantId() {
        var request = new RetrievalRequest("query", null, userId, roles, MetadataFilter.NONE, RetrievalOptions.DEFAULT);

        assertThatNullPointerException()
                .isThrownBy(() -> service.retrieve(request));
    }

    @Test
    void retrieve_rejectsNullRoles() {
        var request = new RetrievalRequest("query", tenantId, userId, null, MetadataFilter.NONE, RetrievalOptions.DEFAULT);

        assertThatNullPointerException()
                .isThrownBy(() -> service.retrieve(request));
    }

    // ── Input validation ──────────────────────────────────────────────────────

    @Test
    void retrieve_rejectsNullQueryText() {
        var request = new RetrievalRequest(null, tenantId, userId, roles, MetadataFilter.NONE, RetrievalOptions.DEFAULT);

        assertThatExceptionOfType(InvalidRetrievalRequestException.class)
                .isThrownBy(() -> service.retrieve(request))
                .withMessageContaining("queryText");
    }

    @Test
    void retrieve_rejectsBlankQueryText() {
        var request = new RetrievalRequest("   ", tenantId, userId, roles, MetadataFilter.NONE, RetrievalOptions.DEFAULT);

        assertThatExceptionOfType(InvalidRetrievalRequestException.class)
                .isThrownBy(() -> service.retrieve(request))
                .withMessageContaining("queryText");
    }

    @Test
    void retrieve_rejectsQueryTextExceedingMaxLength() {
        String longQuery = "x".repeat(10_001);
        var request = new RetrievalRequest(longQuery, tenantId, userId, roles, MetadataFilter.NONE, RetrievalOptions.DEFAULT);

        assertThatExceptionOfType(InvalidRetrievalRequestException.class)
                .isThrownBy(() -> service.retrieve(request))
                .withMessageContaining("queryText");
    }

    // ── Defaults ──────────────────────────────────────────────────────────────

    @Test
    void retrieve_appliesDefaultOptionsWhenNull() {
        var request = new RetrievalRequest("what is a contract?", tenantId, userId, roles, MetadataFilter.NONE, null);
        when(retrievalPort.retrieve("what is a contract?", tenantId, MetadataFilter.NONE, RetrievalOptions.DEFAULT))
                .thenReturn(RetrievalResult.empty("hybrid", 5L, "what is a contract?"));

        service.retrieve(request);

        verify(retrievalPort).retrieve("what is a contract?", tenantId, MetadataFilter.NONE, RetrievalOptions.DEFAULT);
    }

    @Test
    void retrieve_appliesDefaultFilterWhenNull() {
        var request = new RetrievalRequest("what is a contract?", tenantId, userId, roles, null, RetrievalOptions.DEFAULT);
        when(retrievalPort.retrieve("what is a contract?", tenantId, MetadataFilter.NONE, RetrievalOptions.DEFAULT))
                .thenReturn(RetrievalResult.empty("hybrid", 5L, "what is a contract?"));

        service.retrieve(request);

        verify(retrievalPort).retrieve("what is a contract?", tenantId, MetadataFilter.NONE, RetrievalOptions.DEFAULT);
    }

    // ── Happy paths ───────────────────────────────────────────────────────────

    @Test
    void retrieve_skipsRankingAndReturnsRawResultWhenNoHits() {
        var request = new RetrievalRequest("unknown topic", tenantId, userId, roles, MetadataFilter.NONE, RetrievalOptions.DEFAULT);
        when(retrievalPort.retrieve(any(), any(), any(), any()))
                .thenReturn(RetrievalResult.empty("hybrid", 8L, "unknown topic"));

        RetrievalResult result = service.retrieve(request);

        assertThat(result.hasResults()).isFalse();
        verifyNoInteractions(rankingPort);
    }

    @Test
    void retrieve_appliesRankingWhenResultsExist() {
        RetrievedChunk chunk = sampleChunk(0);
        var raw = new RetrievalResult(List.of(chunk), new SearchMetadata(1, 10L, "hybrid"), "what is a contract?");
        when(retrievalPort.retrieve(any(), any(), any(), any())).thenReturn(raw);
        when(rankingPort.rank(List.of(chunk), "what is a contract?")).thenReturn(List.of(chunk));

        var request = new RetrievalRequest("what is a contract?", tenantId, userId, roles, MetadataFilter.NONE, RetrievalOptions.DEFAULT);
        RetrievalResult result = service.retrieve(request);

        assertThat(result.hasResults()).isTrue();
        verify(rankingPort).rank(List.of(chunk), "what is a contract?");
    }

    @Test
    void retrieve_preservesMetadataFromRetrievalPortAfterRanking() {
        RetrievedChunk chunk = sampleChunk(0);
        var metadata = new SearchMetadata(1, 22L, "hybrid");
        var raw = new RetrievalResult(List.of(chunk), metadata, "find policy");
        when(retrievalPort.retrieve(any(), any(), any(), any())).thenReturn(raw);
        when(rankingPort.rank(any(), any())).thenReturn(List.of(chunk));

        var request = new RetrievalRequest("find policy", tenantId, userId, roles, MetadataFilter.NONE, RetrievalOptions.DEFAULT);
        RetrievalResult result = service.retrieve(request);

        assertThat(result.metadata().latencyMs()).isEqualTo(22L);
        assertThat(result.metadata().strategy()).isEqualTo("hybrid");
    }

    // ── Effective query text propagation ──────────────────────────────────────

    @Test
    void retrieve_resultCarriesEffectiveQueryText_whenNoResults() {
        String rewritten = "service level agreement definition";
        when(queryRewritePort.rewrite(anyString(), any())).thenReturn(rewritten);
        when(retrievalPort.retrieve(eq(rewritten), any(), any(), any()))
                .thenReturn(RetrievalResult.empty("hybrid", 5L, rewritten));

        var request = new RetrievalRequest("what's the SLA?", tenantId, userId, roles, MetadataFilter.NONE, RetrievalOptions.DEFAULT);
        RetrievalResult result = service.retrieve(request);

        assertThat(result.effectiveQueryText()).isEqualTo(rewritten);
    }

    @Test
    void retrieve_resultCarriesEffectiveQueryText_afterRanking() {
        String rewritten = "service level agreement violation procedure";
        RetrievedChunk chunk = sampleChunk(0);
        var raw = new RetrievalResult(List.of(chunk), new SearchMetadata(1, 5L, "hybrid"), rewritten);

        when(queryRewritePort.rewrite(anyString(), any())).thenReturn(rewritten);
        when(retrievalPort.retrieve(eq(rewritten), any(), any(), any())).thenReturn(raw);
        when(rankingPort.rank(any(), eq(rewritten))).thenReturn(List.of(chunk));

        var request = new RetrievalRequest("SLA violation?", tenantId, userId, roles, MetadataFilter.NONE, RetrievalOptions.DEFAULT);
        RetrievalResult result = service.retrieve(request);

        assertThat(result.effectiveQueryText()).isEqualTo(rewritten);
    }

    // ── Query rewrite integration ─────────────────────────────────────────────

    @Test
    void retrieve_callsQueryRewritePortBeforeRetrieval() {
        var request = new RetrievalRequest("what's the SLA?", tenantId, userId, roles, MetadataFilter.NONE, RetrievalOptions.DEFAULT);
        when(queryRewritePort.rewrite("what's the SLA?", tenantId))
                .thenReturn("service level agreement definition");
        when(retrievalPort.retrieve(eq("service level agreement definition"), any(), any(), any()))
                .thenReturn(RetrievalResult.empty("hybrid", 5L, "service level agreement definition"));

        service.retrieve(request);

        verify(queryRewritePort).rewrite("what's the SLA?", tenantId);
        verify(retrievalPort).retrieve(eq("service level agreement definition"), any(), any(), any());
    }

    @Test
    void retrieve_passesRewrittenQueryToRetrievalAndRanking() {
        String rewritten = "service level agreement violation procedure";
        RetrievedChunk chunk = sampleChunk(0);
        var raw = new RetrievalResult(List.of(chunk), new SearchMetadata(1, 5L, "hybrid"), rewritten);

        when(queryRewritePort.rewrite(anyString(), any())).thenReturn(rewritten);
        when(retrievalPort.retrieve(eq(rewritten), any(), any(), any())).thenReturn(raw);
        when(rankingPort.rank(any(), eq(rewritten))).thenReturn(List.of(chunk));

        var request = new RetrievalRequest("SLA violation?", tenantId, userId, roles, MetadataFilter.NONE, RetrievalOptions.DEFAULT);
        service.retrieve(request);

        verify(retrievalPort).retrieve(eq(rewritten), any(), any(), any());
        verify(rankingPort).rank(any(), eq(rewritten));
    }

    @Test
    void retrieve_usesOriginalQuery_whenRewriteReturnsOriginalUnchanged() {
        String original = "what is a non-disclosure agreement?";
        when(queryRewritePort.rewrite(original, tenantId)).thenReturn(original);
        when(retrievalPort.retrieve(eq(original), any(), any(), any()))
                .thenReturn(RetrievalResult.empty("hybrid", 3L, original));

        var request = new RetrievalRequest(original, tenantId, userId, roles, MetadataFilter.NONE, RetrievalOptions.DEFAULT);
        service.retrieve(request);

        verify(retrievalPort).retrieve(eq(original), any(), any(), any());
    }

    @Test
    void retrieve_passesTenantIdToQueryRewritePort() {
        var request = new RetrievalRequest("query", tenantId, userId, roles, MetadataFilter.NONE, RetrievalOptions.DEFAULT);
        when(retrievalPort.retrieve(any(), any(), any(), any()))
                .thenReturn(RetrievalResult.empty("hybrid", 1L, "query"));

        service.retrieve(request);

        verify(queryRewritePort).rewrite(anyString(), eq(tenantId));
    }

    // ── Exception consistency (P05.5, ADR HD04) ─────────────────────────────────

    @Test
    void retrieve_infrastructureExceptionFromRetrievalPort_isWrappedAsRetrievalException() {
        var request = new RetrievalRequest("query", tenantId, userId, roles, MetadataFilter.NONE, RetrievalOptions.DEFAULT);
        lenient().when(queryRewritePort.rewrite(anyString(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(retrievalPort.retrieve(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Weaviate connection refused"));

        assertThatExceptionOfType(RetrievalException.class)
                .isThrownBy(() -> service.retrieve(request))
                .withCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void retrieve_infrastructureExceptionFromQueryRewritePort_isWrappedAsRetrievalException() {
        var request = new RetrievalRequest("query", tenantId, userId, roles, MetadataFilter.NONE, RetrievalOptions.DEFAULT);
        when(queryRewritePort.rewrite(anyString(), any())).thenThrow(new RuntimeException("Ollama unreachable"));

        assertThatExceptionOfType(RetrievalException.class)
                .isThrownBy(() -> service.retrieve(request));
    }

    // ── Authorization Filter (P06.2) ─────────────────────────────────────────

    @Test
    void retrieve_dropsChunkWhoseDocumentClassificationExceedsCallerClearance() {
        RetrievedChunk permitted = sampleChunk(0);
        RetrievedChunk denied    = sampleChunk(1);
        var raw = new RetrievalResult(List.of(permitted, denied), new SearchMetadata(2, 5L, "hybrid"), "query");

        Document permittedDoc = documentWithClassification(permitted.documentId(), DocumentClassification.PUBLIC);
        Document deniedDoc    = documentWithClassification(denied.documentId(), DocumentClassification.RESTRICTED);

        when(retrievalPort.retrieve(any(), any(), any(), any())).thenReturn(raw);
        when(documentRepository.findByIds(any())).thenReturn(List.of(permittedDoc, deniedDoc));
        when(classificationPolicyPort.isPermitted(eq(Set.of(UserRole.VIEWER)), eq("PUBLIC"))).thenReturn(true);
        when(classificationPolicyPort.isPermitted(eq(Set.of(UserRole.VIEWER)), eq("RESTRICTED"))).thenReturn(false);
        when(rankingPort.rank(List.of(permitted), "query")).thenReturn(List.of(permitted));

        var request = new RetrievalRequest("query", tenantId, userId, Set.of(UserRole.VIEWER),
                MetadataFilter.NONE, RetrievalOptions.DEFAULT);
        RetrievalResult result = service.retrieve(request);

        assertThat(result.items()).containsExactly(permitted);
        verify(rankingPort).rank(List.of(permitted), "query");
    }

    @Test
    void retrieve_returnsEmptyWithoutRanking_whenEveryChunkIsDenied() {
        RetrievedChunk chunk = sampleChunk(0);
        var raw = new RetrievalResult(List.of(chunk), new SearchMetadata(1, 5L, "hybrid"), "query");

        when(retrievalPort.retrieve(any(), any(), any(), any())).thenReturn(raw);
        when(documentRepository.findByIds(any()))
                .thenReturn(List.of(documentWithClassification(chunk.documentId(), DocumentClassification.RESTRICTED)));
        when(classificationPolicyPort.isPermitted(any(), eq("RESTRICTED"))).thenReturn(false);

        var request = new RetrievalRequest("query", tenantId, userId, Set.of(UserRole.VIEWER),
                MetadataFilter.NONE, RetrievalOptions.DEFAULT);
        RetrievalResult result = service.retrieve(request);

        assertThat(result.hasResults()).isFalse();
        verifyNoInteractions(rankingPort);
    }

    @Test
    void retrieve_deniesChunkWhoseDocumentNoLongerExists() {
        RetrievedChunk chunk = sampleChunk(0);
        var raw = new RetrievalResult(List.of(chunk), new SearchMetadata(1, 5L, "hybrid"), "query");

        when(retrievalPort.retrieve(any(), any(), any(), any())).thenReturn(raw);
        when(documentRepository.findByIds(any())).thenReturn(List.of());
        when(classificationPolicyPort.isPermitted(any(), isNull())).thenReturn(false);

        var request = new RetrievalRequest("query", tenantId, userId, Set.of(UserRole.VIEWER),
                MetadataFilter.NONE, RetrievalOptions.DEFAULT);
        RetrievalResult result = service.retrieve(request);

        assertThat(result.hasResults()).isFalse();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RetrievedChunk sampleChunk(int rank) {
        return new RetrievedChunk(
                ChunkId.generate(),
                DocumentId.generate(),
                tenantId,
                "sample chunk content",
                0.85,
                rank);
    }

    private Document documentWithClassification(DocumentId documentId, DocumentClassification classification) {
        DocumentMetadata metadata = DocumentMetadata.builder().classification(classification).build();
        Document document = Document.reconstitute(
                documentId, tenantId, userId, "file.txt", SupportedFormat.TXT,
                com.mudassirshahzad.eka.domain.document.DocumentStatus.INDEXED, metadata,
                null, null, 1, null, java.time.Instant.now(), java.time.Instant.now(), null);
        return document;
    }
}
