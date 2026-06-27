package com.mudassir.eka.application.retrieval;

import com.mudassir.eka.domain.chunk.ChunkId;
import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.domain.query.MetadataFilter;
import com.mudassir.eka.domain.retrieval.model.RetrievalOptions;
import com.mudassir.eka.domain.retrieval.model.RetrievalResult;
import com.mudassir.eka.domain.retrieval.model.RetrievedChunk;
import com.mudassir.eka.domain.retrieval.model.SearchMetadata;
import com.mudassir.eka.domain.retrieval.port.RankingPort;
import com.mudassir.eka.domain.retrieval.port.RetrievalPort;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetrievalServiceTest {

    @Mock private RetrievalPort retrievalPort;
    @Mock private RankingPort   rankingPort;

    private RetrievalService service;

    private final TenantId tenantId = TenantId.generate();
    private final UserId   userId   = UserId.generate();

    @BeforeEach
    void setUp() {
        service = new RetrievalService(retrievalPort, rankingPort);
    }

    // ── Constructor guards ────────────────────────────────────────────────────

    @Test
    void constructor_rejectsNullRetrievalPort() {
        assertThatNullPointerException()
                .isThrownBy(() -> new RetrievalService(null, rankingPort))
                .withMessageContaining("retrievalPort");
    }

    @Test
    void constructor_rejectsNullRankingPort() {
        assertThatNullPointerException()
                .isThrownBy(() -> new RetrievalService(retrievalPort, null))
                .withMessageContaining("rankingPort");
    }

    // ── Null guards on retrieve() ─────────────────────────────────────────────

    @Test
    void retrieve_rejectsNullRequest() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.retrieve(null));
    }

    @Test
    void retrieve_rejectsNullTenantId() {
        var request = new RetrievalRequest("query", null, userId, MetadataFilter.NONE, RetrievalOptions.DEFAULT);

        assertThatNullPointerException()
                .isThrownBy(() -> service.retrieve(request));
    }

    // ── Input validation ──────────────────────────────────────────────────────

    @Test
    void retrieve_rejectsNullQueryText() {
        var request = new RetrievalRequest(null, tenantId, userId, MetadataFilter.NONE, RetrievalOptions.DEFAULT);

        assertThatExceptionOfType(InvalidRetrievalRequestException.class)
                .isThrownBy(() -> service.retrieve(request))
                .withMessageContaining("queryText");
    }

    @Test
    void retrieve_rejectsBlankQueryText() {
        var request = new RetrievalRequest("   ", tenantId, userId, MetadataFilter.NONE, RetrievalOptions.DEFAULT);

        assertThatExceptionOfType(InvalidRetrievalRequestException.class)
                .isThrownBy(() -> service.retrieve(request))
                .withMessageContaining("queryText");
    }

    @Test
    void retrieve_rejectsQueryTextExceedingMaxLength() {
        String longQuery = "x".repeat(10_001);
        var request = new RetrievalRequest(longQuery, tenantId, userId, MetadataFilter.NONE, RetrievalOptions.DEFAULT);

        assertThatExceptionOfType(InvalidRetrievalRequestException.class)
                .isThrownBy(() -> service.retrieve(request))
                .withMessageContaining("queryText");
    }

    // ── Defaults ──────────────────────────────────────────────────────────────

    @Test
    void retrieve_appliesDefaultOptionsWhenNull() {
        var request = new RetrievalRequest("what is a contract?", tenantId, userId, MetadataFilter.NONE, null);
        when(retrievalPort.retrieve("what is a contract?", tenantId, MetadataFilter.NONE, RetrievalOptions.DEFAULT))
                .thenReturn(RetrievalResult.empty("vector", 5L));

        service.retrieve(request);

        verify(retrievalPort).retrieve("what is a contract?", tenantId, MetadataFilter.NONE, RetrievalOptions.DEFAULT);
    }

    @Test
    void retrieve_appliesDefaultFilterWhenNull() {
        var request = new RetrievalRequest("what is a contract?", tenantId, userId, null, RetrievalOptions.DEFAULT);
        when(retrievalPort.retrieve("what is a contract?", tenantId, MetadataFilter.NONE, RetrievalOptions.DEFAULT))
                .thenReturn(RetrievalResult.empty("vector", 5L));

        service.retrieve(request);

        verify(retrievalPort).retrieve("what is a contract?", tenantId, MetadataFilter.NONE, RetrievalOptions.DEFAULT);
    }

    // ── Happy paths ───────────────────────────────────────────────────────────

    @Test
    void retrieve_skipsRankingAndReturnsRawResultWhenNoHits() {
        var request = new RetrievalRequest("unknown topic", tenantId, userId, MetadataFilter.NONE, RetrievalOptions.DEFAULT);
        when(retrievalPort.retrieve(any(), any(), any(), any()))
                .thenReturn(RetrievalResult.empty("vector", 8L));

        RetrievalResult result = service.retrieve(request);

        assertThat(result.hasResults()).isFalse();
        verifyNoInteractions(rankingPort);
    }

    @Test
    void retrieve_appliesRankingWhenResultsExist() {
        RetrievedChunk chunk = sampleChunk(0);
        var raw = new RetrievalResult(List.of(chunk), new SearchMetadata(1, 10L, "vector"));
        when(retrievalPort.retrieve(any(), any(), any(), any())).thenReturn(raw);
        when(rankingPort.rank(raw.items(), "what is a contract?")).thenReturn(List.of(chunk));

        var request = new RetrievalRequest("what is a contract?", tenantId, userId, MetadataFilter.NONE, RetrievalOptions.DEFAULT);
        RetrievalResult result = service.retrieve(request);

        assertThat(result.hasResults()).isTrue();
        verify(rankingPort).rank(raw.items(), "what is a contract?");
    }

    @Test
    void retrieve_preservesMetadataFromRetrievalPortAfterRanking() {
        RetrievedChunk chunk = sampleChunk(0);
        var metadata = new SearchMetadata(1, 22L, "hybrid");
        var raw = new RetrievalResult(List.of(chunk), metadata);
        when(retrievalPort.retrieve(any(), any(), any(), any())).thenReturn(raw);
        when(rankingPort.rank(any(), any())).thenReturn(List.of(chunk));

        var request = new RetrievalRequest("find policy", tenantId, userId, MetadataFilter.NONE, RetrievalOptions.DEFAULT);
        RetrievalResult result = service.retrieve(request);

        assertThat(result.metadata().latencyMs()).isEqualTo(22L);
        assertThat(result.metadata().strategy()).isEqualTo("hybrid");
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
}
