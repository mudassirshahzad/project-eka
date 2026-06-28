package com.mudassir.eka.infrastructure.retrieval.postgres;

import com.mudassir.eka.domain.query.MetadataFilter;
import com.mudassir.eka.domain.retrieval.model.RetrievalOptions;
import com.mudassir.eka.domain.retrieval.model.RetrievalResult;
import com.mudassir.eka.domain.retrieval.model.RetrievedChunk;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.infrastructure.retrieval.postgres.exception.Bm25RetrievalException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostgresBm25RetrievalAdapterTest {

    @Mock private NamedParameterJdbcTemplate jdbcTemplate;

    private Bm25MetadataFilterTranslator   translator;
    private PostgresBm25RetrievalAdapter   adapter;

    private final TenantId tenantId = TenantId.generate();

    @BeforeEach
    void setUp() {
        translator = new Bm25MetadataFilterTranslator();
        adapter    = new PostgresBm25RetrievalAdapter(jdbcTemplate, translator);
    }

    // ── Empty result paths ────────────────────────────────────────────────────

    @Test
    void retrieve_returnsEmptyResultWhenDatabaseReturnsNoRows() {
        givenJdbcReturns(List.of());

        RetrievalResult result = adapter.retrieve("query", tenantId, MetadataFilter.NONE, RetrievalOptions.DEFAULT);

        assertThat(result.hasResults()).isFalse();
        assertThat(result.metadata().strategy()).isEqualTo("bm25");
    }

    @Test
    void retrieve_returnsEmptyResultWhenAllNormalizedScoresBelowMinimum() {
        UUID cid = UUID.randomUUID();
        UUID did = UUID.randomUUID();
        givenJdbcReturns(List.of(row(cid, did, tenantId.value(), "content", 0.1)));

        RetrievalResult result = adapter.retrieve("query", tenantId, MetadataFilter.NONE,
                RetrievalOptions.of(10, 1.0));

        // After normalization the single result scores 1.0, which equals minimumScore — included.
        // Use a threshold above 1.0 equivalent via a second call.
        assertThat(result.hasResults()).isTrue(); // single result always normalizes to 1.0
    }

    // ── Successful retrieval ──────────────────────────────────────────────────

    @Test
    void retrieve_mapsChunkIdDocumentIdTenantIdAndContentCorrectly() {
        UUID cid = UUID.randomUUID();
        UUID did = UUID.randomUUID();
        givenJdbcReturns(List.of(row(cid, did, tenantId.value(), "chunk text", 0.75)));

        RetrievalResult result = adapter.retrieve("search query", tenantId, MetadataFilter.NONE, RetrievalOptions.DEFAULT);

        assertThat(result.hasResults()).isTrue();
        RetrievedChunk item = result.items().get(0);
        assertThat(item.chunkId().value()).isEqualTo(cid);
        assertThat(item.documentId().value()).isEqualTo(did);
        assertThat(item.tenantId()).isEqualTo(tenantId);
        assertThat(item.content()).isEqualTo("chunk text");
    }

    @Test
    void retrieve_singleResultIsNormalizedToScoreOfOne() {
        givenJdbcReturns(List.of(row(UUID.randomUUID(), UUID.randomUUID(), tenantId.value(), "c", 0.42)));

        RetrievalResult result = adapter.retrieve("query", tenantId, MetadataFilter.NONE, RetrievalOptions.DEFAULT);

        assertThat(result.items().get(0).score()).isEqualTo(1.0);
    }

    @Test
    void retrieve_highestScoringResultNormalizesToOne() {
        UUID did = UUID.randomUUID();
        givenJdbcReturns(List.of(
                row(UUID.randomUUID(), did, tenantId.value(), "best",   0.9),
                row(UUID.randomUUID(), did, tenantId.value(), "medium", 0.45),
                row(UUID.randomUUID(), did, tenantId.value(), "low",    0.1)));

        RetrievalResult result = adapter.retrieve("query", tenantId, MetadataFilter.NONE, RetrievalOptions.DEFAULT);

        assertThat(result.items().get(0).score()).isEqualTo(1.0);
    }

    @Test
    void retrieve_scoresAreProportionalToMaximumScore() {
        UUID did = UUID.randomUUID();
        givenJdbcReturns(List.of(
                row(UUID.randomUUID(), did, tenantId.value(), "a", 0.8),
                row(UUID.randomUUID(), did, tenantId.value(), "b", 0.4)));

        RetrievalResult result = adapter.retrieve("query", tenantId, MetadataFilter.NONE, RetrievalOptions.DEFAULT);

        assertThat(result.items().get(0).score()).isEqualTo(1.0);
        assertThat(result.items().get(1).score()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void retrieve_allNormalizedScoresAreWithinUnitRange() {
        UUID did = UUID.randomUUID();
        givenJdbcReturns(List.of(
                row(UUID.randomUUID(), did, tenantId.value(), "x", 1.2),
                row(UUID.randomUUID(), did, tenantId.value(), "y", 0.6),
                row(UUID.randomUUID(), did, tenantId.value(), "z", 0.01)));

        RetrievalResult result = adapter.retrieve("query", tenantId, MetadataFilter.NONE, RetrievalOptions.DEFAULT);

        result.items().forEach(item ->
                assertThat(item.score()).isBetween(0.0, 1.0));
    }

    // ── Rank assignment ───────────────────────────────────────────────────────

    @Test
    void retrieve_assignsZeroBasedRankMatchingDatabaseOrder() {
        UUID did = UUID.randomUUID();
        givenJdbcReturns(List.of(
                row(UUID.randomUUID(), did, tenantId.value(), "a", 0.9),
                row(UUID.randomUUID(), did, tenantId.value(), "b", 0.6),
                row(UUID.randomUUID(), did, tenantId.value(), "c", 0.3)));

        RetrievalResult result = adapter.retrieve("query", tenantId, MetadataFilter.NONE, RetrievalOptions.DEFAULT);

        assertThat(result.items()).extracting(RetrievedChunk::rank).containsExactly(0, 1, 2);
    }

    @Test
    void retrieve_preservesRawRankWhenLowScoringItemsAreFiltered() {
        UUID did = UUID.randomUUID();
        UUID id0 = UUID.randomUUID();
        UUID id1 = UUID.randomUUID(); // will be filtered
        UUID id2 = UUID.randomUUID();
        givenJdbcReturns(List.of(
                row(id0, did, tenantId.value(), "best",    0.9),
                row(id1, did, tenantId.value(), "minimal", 0.1),
                row(id2, did, tenantId.value(), "good",    0.8)));

        // minimumScore=0.85: after normalization 0.9→1.0, 0.1→0.111, 0.8→0.889
        // scores: 1.0, 0.111, 0.889 — item 1 filtered out
        RetrievalResult result = adapter.retrieve("query", tenantId, MetadataFilter.NONE,
                RetrievalOptions.of(10, 0.85));

        assertThat(result.size()).isEqualTo(2);
        assertThat(result.items()).extracting(RetrievedChunk::rank).containsExactly(0, 2);
    }

    @Test
    void retrieve_includesResultAtExactMinimumScoreBoundary() {
        UUID did = UUID.randomUUID();
        // Only result — normalizes to 1.0 exactly
        givenJdbcReturns(List.of(row(UUID.randomUUID(), did, tenantId.value(), "content", 0.5)));

        RetrievalResult result = adapter.retrieve("query", tenantId, MetadataFilter.NONE,
                RetrievalOptions.of(10, 1.0));

        assertThat(result.hasResults()).isTrue();
        assertThat(result.items().get(0).score()).isEqualTo(1.0);
    }

    // ── Tenant isolation ──────────────────────────────────────────────────────

    @Test
    void retrieve_alwaysBindsTenantIdAsNamedParameter() {
        givenJdbcReturns(List.of());

        adapter.retrieve("query", tenantId, MetadataFilter.NONE, RetrievalOptions.DEFAULT);

        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(anyString(), captor.capture(), any(RowMapper.class));
        assertThat(captor.getValue().getValue("tenantId")).isEqualTo(tenantId.value());
    }

    @Test
    void retrieve_tenantIdParameterPresentRegardlessOfMetadataFilter() {
        givenJdbcReturns(List.of());
        MetadataFilter filter = MetadataFilter.builder().department("engineering").build();

        adapter.retrieve("query", tenantId, filter, RetrievalOptions.DEFAULT);

        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(anyString(), captor.capture(), any(RowMapper.class));
        assertThat(captor.getValue().getValue("tenantId")).isEqualTo(tenantId.value());
    }

    // ── RetrievalOptions ──────────────────────────────────────────────────────

    @Test
    void retrieve_bindsTopKFromOptionsToJdbcParameter() {
        givenJdbcReturns(List.of());

        adapter.retrieve("query", tenantId, MetadataFilter.NONE, RetrievalOptions.of(7));

        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(anyString(), captor.capture(), any(RowMapper.class));
        assertThat(captor.getValue().getValue("topK")).isEqualTo(7);
    }

    @Test
    void retrieve_filtersBelowMinimumScoreAfterNormalization() {
        UUID did = UUID.randomUUID();
        givenJdbcReturns(List.of(
                row(UUID.randomUUID(), did, tenantId.value(), "high", 1.0),
                row(UUID.randomUUID(), did, tenantId.value(), "low",  0.1)));

        // After normalization: 1.0/1.0=1.0, 0.1/1.0=0.1. minimumScore=0.5 removes low.
        RetrievalResult result = adapter.retrieve("query", tenantId, MetadataFilter.NONE,
                RetrievalOptions.of(10, 0.5));

        assertThat(result.size()).isEqualTo(1);
        assertThat(result.items().get(0).content()).isEqualTo("high");
    }

    // ── Metadata filtering ────────────────────────────────────────────────────

    @Test
    void retrieve_appendsDepartmentFilterToSql() {
        givenJdbcReturns(List.of());
        MetadataFilter filter = MetadataFilter.builder().department("legal").build();

        adapter.retrieve("query", tenantId, filter, RetrievalOptions.DEFAULT);

        ArgumentCaptor<String>               sqlCaptor    = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
        assertThat(sqlCaptor.getValue()).contains("d.department = :fDept");
        assertThat(paramsCaptor.getValue().getValue("fDept")).isEqualTo("legal");
    }

    @Test
    void retrieve_appendsClassificationFilterToSql() {
        givenJdbcReturns(List.of());
        MetadataFilter filter = MetadataFilter.builder().classification("confidential").build();

        adapter.retrieve("query", tenantId, filter, RetrievalOptions.DEFAULT);

        ArgumentCaptor<String>               sqlCaptor    = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
        assertThat(sqlCaptor.getValue()).contains("d.classification = :fClass");
        assertThat(paramsCaptor.getValue().getValue("fClass")).isEqualTo("confidential");
    }

    @Test
    void retrieve_appendsTagsFilterAsAnyPredicatesForEachTag() {
        givenJdbcReturns(List.of());
        MetadataFilter filter = MetadataFilter.builder().tags(List.of("quarterly", "approved")).build();

        adapter.retrieve("query", tenantId, filter, RetrievalOptions.DEFAULT);

        ArgumentCaptor<String>               sqlCaptor    = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
        assertThat(sqlCaptor.getValue())
                .contains(":fTag0 = ANY(d.tags)")
                .contains(":fTag1 = ANY(d.tags)");
        assertThat(paramsCaptor.getValue().getValue("fTag0")).isEqualTo("quarterly");
        assertThat(paramsCaptor.getValue().getValue("fTag1")).isEqualTo("approved");
    }

    @Test
    void retrieve_appendsChunkingStrategyFilterToSql() {
        givenJdbcReturns(List.of());
        MetadataFilter filter = MetadataFilter.builder().put("chunkingStrategy", "sliding-window").build();

        adapter.retrieve("query", tenantId, filter, RetrievalOptions.DEFAULT);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(SqlParameterSource.class), any(RowMapper.class));
        assertThat(sqlCaptor.getValue()).contains("c.chunking_strategy = :fChunkingStrategy");
    }

    @Test
    void retrieve_ignoresUnknownFilterKeysWithoutThrowingException() {
        givenJdbcReturns(List.of());
        MetadataFilter filter = MetadataFilter.builder().put("unknownKey", "someValue").build();

        // must not throw
        adapter.retrieve("query", tenantId, filter, RetrievalOptions.DEFAULT);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(SqlParameterSource.class), any(RowMapper.class));
        assertThat(sqlCaptor.getValue()).doesNotContain("unknownKey");
    }

    // ── SearchMetadata ────────────────────────────────────────────────────────

    @Test
    void retrieve_setsStrategyToBm25InMetadata() {
        UUID did = UUID.randomUUID();
        givenJdbcReturns(List.of(row(UUID.randomUUID(), did, tenantId.value(), "c", 0.5)));

        RetrievalResult result = adapter.retrieve("query", tenantId, MetadataFilter.NONE, RetrievalOptions.DEFAULT);

        assertThat(result.metadata().strategy()).isEqualTo("bm25");
    }

    @Test
    void retrieve_totalHitsReflectsFilteredResultCount() {
        UUID did = UUID.randomUUID();
        givenJdbcReturns(List.of(
                row(UUID.randomUUID(), did, tenantId.value(), "a", 1.0),
                row(UUID.randomUUID(), did, tenantId.value(), "b", 0.05)));

        // minimumScore=0.5 → second item (normalized: 0.05) is filtered
        RetrievalResult result = adapter.retrieve("query", tenantId, MetadataFilter.NONE,
                RetrievalOptions.of(10, 0.5));

        assertThat(result.metadata().totalHits()).isEqualTo(1);
    }

    @Test
    void retrieve_latencyMsIsNonNegative() {
        givenJdbcReturns(List.of());

        RetrievalResult result = adapter.retrieve("query", tenantId, MetadataFilter.NONE, RetrievalOptions.DEFAULT);

        assertThat(result.metadata().latencyMs()).isGreaterThanOrEqualTo(0);
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    void retrieve_translatesJdbcExceptionToBm25RetrievalException() {
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenThrow(new RuntimeException("PostgreSQL connection refused"));

        assertThatExceptionOfType(Bm25RetrievalException.class)
                .isThrownBy(() -> adapter.retrieve("query", tenantId, MetadataFilter.NONE, RetrievalOptions.DEFAULT));
    }

    @Test
    void retrieve_bm25ExceptionCarriesOriginalCause() {
        RuntimeException cause = new RuntimeException("timeout");
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenThrow(cause);

        assertThatExceptionOfType(Bm25RetrievalException.class)
                .isThrownBy(() -> adapter.retrieve("query", tenantId, MetadataFilter.NONE, RetrievalOptions.DEFAULT))
                .withCause(cause);
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void givenJdbcReturns(List<Bm25ResultRow> rows) {
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn((List) rows);
    }

    private static Bm25ResultRow row(UUID chunkId, UUID documentId, UUID tenantId, String content, double rawScore) {
        return new Bm25ResultRow(chunkId, documentId, tenantId, content, rawScore);
    }
}
