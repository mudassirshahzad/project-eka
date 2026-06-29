package com.mudassir.eka.infrastructure.ranking;

import com.mudassir.eka.domain.chunk.ChunkId;
import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.domain.retrieval.model.RetrievedChunk;
import com.mudassir.eka.domain.shared.TenantId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

class RrfRankingAdapterTest {

    private static final int K = 60;

    private final RrfRankingAdapter adapter = new RrfRankingAdapter(K);

    private final TenantId   tenantId = TenantId.generate();
    private final DocumentId docId    = DocumentId.generate();

    // ── Null and empty inputs ─────────────────────────────────────────────────

    @Test
    void rank_returnsEmptyListForNullInput() {
        assertThat(adapter.rank(null, "query")).isEmpty();
    }

    @Test
    void rank_returnsEmptyListForEmptyInput() {
        assertThat(adapter.rank(List.of(), "query")).isEmpty();
    }

    // ── Single item ───────────────────────────────────────────────────────────

    @Test
    void rank_singleItemReceivesNormalizedScoreOfOne() {
        ChunkId id = ChunkId.generate();

        List<RetrievedChunk> result = adapter.rank(List.of(chunk(id, 3)), "query");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).score()).isEqualTo(1.0);
    }

    @Test
    void rank_singleItemReceivesRankZero() {
        List<RetrievedChunk> result = adapter.rank(List.of(chunk(ChunkId.generate(), 7)), "query");

        assertThat(result.get(0).rank()).isEqualTo(0);
    }

    // ── Multiple unique items (no duplicates) ─────────────────────────────────

    @Test
    void rank_sortsByDescendingRrfScore() {
        ChunkId idA = ChunkId.generate();
        ChunkId idB = ChunkId.generate();
        ChunkId idC = ChunkId.generate();

        List<RetrievedChunk> result = adapter.rank(List.of(
                chunk(idA, 2), // lowest RRF
                chunk(idB, 0), // highest RRF
                chunk(idC, 1)  // middle RRF
        ), "query");

        assertThat(result.get(0).chunkId()).isEqualTo(idB);
        assertThat(result.get(1).chunkId()).isEqualTo(idC);
        assertThat(result.get(2).chunkId()).isEqualTo(idA);
    }

    @Test
    void rank_assignsSequentialZeroBasedRanksInOutput() {
        List<RetrievedChunk> result = adapter.rank(List.of(
                chunk(ChunkId.generate(), 0),
                chunk(ChunkId.generate(), 1),
                chunk(ChunkId.generate(), 2)
        ), "query");

        assertThat(result).extracting(RetrievedChunk::rank).containsExactly(0, 1, 2);
    }

    @Test
    void rank_higherOriginalRankProducesLowerRrfContribution() {
        ChunkId idA = ChunkId.generate();
        ChunkId idB = ChunkId.generate();

        List<RetrievedChunk> result = adapter.rank(List.of(
                chunk(idA, 0),
                chunk(idB, 5)
        ), "query");

        // A has better rank → higher RRF score → higher normalized score → comes first
        assertThat(result.get(0).chunkId()).isEqualTo(idA);
        assertThat(result.get(0).score()).isGreaterThan(result.get(1).score());
    }

    // ── Mathematical correctness ──────────────────────────────────────────────

    @Test
    void rank_secondItemScoreMatchesRrfFormula() {
        // Two unique chunks at ranks 0 and 1.
        // Raw RRF: A = 1/(K+0) = 1/60, B = 1/(K+1) = 1/61
        // Normalized: A = 1.0, B = (1/61) / (1/60) = 60/61
        ChunkId idA = ChunkId.generate();
        ChunkId idB = ChunkId.generate();

        List<RetrievedChunk> result = adapter.rank(List.of(
                chunk(idA, 0),
                chunk(idB, 1)
        ), "query");

        double expectedSecondScore = (double) K / (K + 1);
        assertThat(result.get(1).score()).isCloseTo(expectedSecondScore, within(1e-9));
    }

    @Test
    void rank_twoOccurrencesAtSameRankProducesDoubleContribution() {
        // Chunk A appears twice at rank 5; Chunk B appears once at rank 5.
        // Raw RRF: A = 2/(K+5), B = 1/(K+5)
        // Normalized: A = 1.0, B = 0.5
        ChunkId idA = ChunkId.generate();
        ChunkId idB = ChunkId.generate();

        List<RetrievedChunk> result = adapter.rank(List.of(
                chunk(idA, 5),
                chunk(idB, 5),
                chunk(idA, 5)  // second occurrence of A
        ), "query");

        assertThat(result.get(0).chunkId()).isEqualTo(idA);
        assertThat(result.get(0).score()).isEqualTo(1.0);
        assertThat(result.get(1).chunkId()).isEqualTo(idB);
        assertThat(result.get(1).score()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void rank_summedRrfScoreMatchesFormulaExactly() {
        // Chunk A at ranks 0 and 3 from two retrieval lists.
        // Expected raw RRF: 1/(K+0) + 1/(K+3) = 1/60 + 1/63
        // Chunk B at rank 1 (single list).
        // Expected raw RRF: 1/(K+1) = 1/61
        // Max = 1/60 + 1/63
        // Normalized B = (1/61) / (1/60 + 1/63)
        ChunkId idA = ChunkId.generate();
        ChunkId idB = ChunkId.generate();

        double rawA = 1.0 / (K + 0) + 1.0 / (K + 3);
        double rawB = 1.0 / (K + 1);

        List<RetrievedChunk> result = adapter.rank(List.of(
                chunk(idA, 0),
                chunk(idB, 1),
                chunk(idA, 3)
        ), "query");

        assertThat(result.get(0).chunkId()).isEqualTo(idA);
        assertThat(result.get(0).score()).isEqualTo(1.0);
        assertThat(result.get(1).chunkId()).isEqualTo(idB);
        assertThat(result.get(1).score()).isCloseTo(rawB / rawA, within(1e-9));
    }

    // ── Duplicate chunk handling ───────────────────────────────────────────────

    @Test
    void rank_duplicateChunksByChunkIdProduceOneOutputEntry() {
        ChunkId id = ChunkId.generate();

        List<RetrievedChunk> result = adapter.rank(List.of(
                chunk(id, 0),
                chunk(id, 2),
                chunk(id, 5)
        ), "query");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).chunkId()).isEqualTo(id);
    }

    @Test
    void rank_chunkFoundByBothEnginesOutranksChunkFoundByOneEngine() {
        ChunkId idA = ChunkId.generate(); // found by two engines
        ChunkId idB = ChunkId.generate(); // found by one engine at rank 0

        // B has the best single-engine rank (0), but A appears in both engines.
        List<RetrievedChunk> result = adapter.rank(List.of(
                chunk(idA, 1),
                chunk(idB, 0),
                chunk(idA, 1)  // second engine also returns A at rank 1
        ), "query");

        // A: 2*(1/61) = 2/61 ≈ 0.03279
        // B: 1/60     = 1/60 ≈ 0.01667
        // 2/61 > 1/60 → A wins
        assertThat(result.get(0).chunkId()).isEqualTo(idA);
    }

    @Test
    void rank_preservesFirstOccurrenceMetadataForMergedChunk() {
        ChunkId    id        = ChunkId.generate();
        DocumentId firstDoc  = DocumentId.generate();
        DocumentId secondDoc = DocumentId.generate();

        List<RetrievedChunk> candidates = List.of(
                new RetrievedChunk(id, firstDoc,  tenantId, "first content",  0.9, 0),
                new RetrievedChunk(id, secondDoc, tenantId, "second content", 0.8, 2)
        );

        RetrievedChunk merged = adapter.rank(candidates, "query").get(0);

        assertThat(merged.documentId()).isEqualTo(firstDoc);
        assertThat(merged.content()).isEqualTo("first content");
    }

    // ── Score normalization ───────────────────────────────────────────────────

    @Test
    void rank_highestRrfScoredChunkAlwaysNormalizesToOne() {
        List<RetrievedChunk> result = adapter.rank(List.of(
                chunk(ChunkId.generate(), 0),
                chunk(ChunkId.generate(), 3),
                chunk(ChunkId.generate(), 7)
        ), "query");

        assertThat(result.get(0).score()).isEqualTo(1.0);
    }

    @Test
    void rank_allNormalizedScoresAreWithinUnitRange() {
        List<RetrievedChunk> result = adapter.rank(List.of(
                chunk(ChunkId.generate(), 0),
                chunk(ChunkId.generate(), 0),  // duplicate
                chunk(ChunkId.generate(), 1),
                chunk(ChunkId.generate(), 99)
        ), "query");

        result.forEach(item -> assertThat(item.score()).isBetween(0.0, 1.0));
    }

    @Test
    void rank_normalizedScoresPreserveProportionalRrfRatios() {
        // Two chunks: A at rank 0, B at rank 0 (from two separate lists, A merged).
        // A appears twice at rank 0 → raw 2/60; B once at rank 0 → raw 1/60.
        // Ratio A:B must be 2:1 in normalized scores.
        ChunkId idA = ChunkId.generate();
        ChunkId idB = ChunkId.generate();

        List<RetrievedChunk> result = adapter.rank(List.of(
                chunk(idA, 0),
                chunk(idB, 0),
                chunk(idA, 0)
        ), "query");

        assertThat(result.get(0).score() / result.get(1).score())
                .isCloseTo(2.0, within(1e-9));
    }

    // ── Tie-breaking ──────────────────────────────────────────────────────────

    @Test
    void rank_tiesAreBrokenByChunkIdUuidInAscendingLexicographicOrder() {
        // Use fixed UUIDs to make the tie-breaking deterministic in the assertion.
        ChunkId smallerId = ChunkId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        ChunkId largerId  = ChunkId.of(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"));

        // Both at rank 0 → identical RRF score; tie-break must put smaller UUID first.
        List<RetrievedChunk> result = adapter.rank(List.of(
                chunk(largerId,  0),  // put larger first to verify sort is not input-order
                chunk(smallerId, 0)
        ), "query");

        assertThat(result.get(0).chunkId()).isEqualTo(smallerId);
        assertThat(result.get(1).chunkId()).isEqualTo(largerId);
    }

    @Test
    void rank_tieBreakingIsStableAcrossRepeatedCalls() {
        ChunkId idA = ChunkId.of(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        ChunkId idB = ChunkId.of(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));

        List<RetrievedChunk> candidates = List.of(chunk(idB, 3), chunk(idA, 3));

        List<RetrievedChunk> first  = adapter.rank(candidates, "query");
        List<RetrievedChunk> second = adapter.rank(candidates, "query");

        assertThat(first.get(0).chunkId()).isEqualTo(second.get(0).chunkId());
        assertThat(first.get(1).chunkId()).isEqualTo(second.get(1).chunkId());
    }

    // ── Different k values ────────────────────────────────────────────────────

    @Test
    void rank_largerKDiminishesTopRankAdvantage() {
        // With k=1: score ratio between rank 0 and rank 1 is (1/1) / (1/2) = 2.0
        // With k=60: score ratio is (1/60) / (1/61) = 61/60 ≈ 1.017
        // Larger k → ratio closer to 1 (less discrimination between ranks).
        ChunkId idA = ChunkId.generate();
        ChunkId idB = ChunkId.generate();

        List<RetrievedChunk> candidates = List.of(chunk(idA, 0), chunk(idB, 1));

        RrfRankingAdapter lowK  = new RrfRankingAdapter(1);
        RrfRankingAdapter highK = new RrfRankingAdapter(1000);

        double ratioLowK  = lowK.rank(candidates, "q").get(0).score()
                          / lowK.rank(candidates, "q").get(1).score();
        double ratioHighK = highK.rank(candidates, "q").get(0).score()
                          / highK.rank(candidates, "q").get(1).score();

        assertThat(ratioLowK).isGreaterThan(ratioHighK);
    }

    @Test
    void rank_kOfOneMaximisesRankPositionDiscrimination() {
        // k=1, rank 0 vs rank 1: ratio = (1/(1+0)) / (1/(1+1)) = 1 / 0.5 = 2.0
        ChunkId idA = ChunkId.generate();
        ChunkId idB = ChunkId.generate();

        RrfRankingAdapter kOne = new RrfRankingAdapter(1);
        List<RetrievedChunk> result = kOne.rank(List.of(chunk(idA, 0), chunk(idB, 1)), "q");

        assertThat(result.get(1).score()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void rank_constructorRejectsKLessThanOne() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RrfRankingAdapter(0))
                .withMessageContaining("k must be >= 1");
    }

    // ── Output rank semantics ─────────────────────────────────────────────────

    @Test
    void rank_outputRanksAreZeroBasedPositionsInFusedList() {
        List<RetrievedChunk> result = adapter.rank(List.of(
                chunk(ChunkId.generate(), 0),
                chunk(ChunkId.generate(), 1),
                chunk(ChunkId.generate(), 2),
                chunk(ChunkId.generate(), 3)
        ), "query");

        assertThat(result).extracting(RetrievedChunk::rank).containsExactly(0, 1, 2, 3);
    }

    @Test
    void rank_queryTextParameterIsUnusedAndNullIsAccepted() {
        ChunkId idA = ChunkId.generate();
        ChunkId idB = ChunkId.generate();
        List<RetrievedChunk> candidates = List.of(chunk(idA, 0), chunk(idB, 1));

        List<RetrievedChunk> withText = adapter.rank(candidates, "some query");
        List<RetrievedChunk> withNull = adapter.rank(candidates, null);

        assertThat(withText.get(0).chunkId()).isEqualTo(withNull.get(0).chunkId());
        assertThat(withText.get(1).chunkId()).isEqualTo(withNull.get(1).chunkId());
    }

    // ── Classic RRF fusion example ─────────────────────────────────────────────

    @Test
    void rank_classicFusionExampleWithFourChunksAndOneDuplicate() {
        // Simulates merging BM25 and vector result lists:
        //   BM25:   ChunkA(rank=0), ChunkB(rank=1), ChunkC(rank=2), ChunkD(rank=3)
        //   Vector: ChunkA(rank=0)
        //
        // With k=60:
        //   A: 1/60 + 1/60 = 2/60      (appears in both lists)
        //   B: 1/61
        //   C: 1/62
        //   D: 1/63
        //
        // Normalized (max = 2/60):
        //   A: 1.0
        //   B: (1/61) / (2/60) = 60/(2*61) = 30/61
        //   C: (1/62) / (2/60) = 60/(2*62) = 30/62
        //   D: (1/63) / (2/60) = 60/(2*63) = 30/63
        ChunkId idA = ChunkId.generate();
        ChunkId idB = ChunkId.generate();
        ChunkId idC = ChunkId.generate();
        ChunkId idD = ChunkId.generate();

        List<RetrievedChunk> candidates = List.of(
                chunk(idA, 0),  // BM25 rank 0
                chunk(idB, 1),  // BM25 rank 1
                chunk(idC, 2),  // BM25 rank 2
                chunk(idD, 3),  // BM25 rank 3
                chunk(idA, 0)   // Vector rank 0 (duplicate of A)
        );

        List<RetrievedChunk> result = adapter.rank(candidates, "query");

        assertThat(result).hasSize(4);

        assertThat(result.get(0).chunkId()).isEqualTo(idA);
        assertThat(result.get(0).score()).isEqualTo(1.0);
        assertThat(result.get(0).rank()).isEqualTo(0);

        assertThat(result.get(1).chunkId()).isEqualTo(idB);
        assertThat(result.get(1).score()).isCloseTo(30.0 / 61.0, within(1e-9));
        assertThat(result.get(1).rank()).isEqualTo(1);

        assertThat(result.get(2).chunkId()).isEqualTo(idC);
        assertThat(result.get(2).score()).isCloseTo(30.0 / 62.0, within(1e-9));
        assertThat(result.get(2).rank()).isEqualTo(2);

        assertThat(result.get(3).chunkId()).isEqualTo(idD);
        assertThat(result.get(3).score()).isCloseTo(30.0 / 63.0, within(1e-9));
        assertThat(result.get(3).rank()).isEqualTo(3);
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private RetrievedChunk chunk(ChunkId id, int rank) {
        return new RetrievedChunk(id, docId, tenantId, "content-" + id.value(), 1.0, rank);
    }
}
