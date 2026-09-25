package com.mudassirshahzad.eka.evaluation;

import com.mudassirshahzad.eka.application.evaluation.RetrievalMetrics;
import com.mudassirshahzad.eka.domain.chunk.ChunkId;
import com.mudassirshahzad.eka.domain.document.DocumentId;
import com.mudassirshahzad.eka.domain.generation.model.FinishReason;
import com.mudassirshahzad.eka.domain.generation.model.LlmResponse;
import com.mudassirshahzad.eka.domain.generation.port.LlmPort;
import com.mudassirshahzad.eka.domain.retrieval.model.RetrievedChunk;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.infrastructure.rerank.LlmRerankAdapter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The retrieval-quality benchmark harness (WP-4, ADR RQ05/RQ06).
 *
 * <h3>What this measures, and what it does not</h3>
 * <p>It measures whether re-ranking, wired through the real {@link LlmRerankAdapter}, improves
 * ranking metrics over a baseline ordering on {@link SyntheticEvaluationDataset}. It does
 * <strong>not</strong> measure Project EKA's production retrieval quality: the dataset is synthetic
 * and the model is stubbed. Those numbers are an engineering baseline — a deterministic regression
 * guard and a proof that the harness, the metrics, and the re-rank integration all work — not
 * evidence about real-world relevance.
 *
 * <h3>Why the stub scorer is not the answer key</h3>
 * <p>Scoring with the relevance labels would make improvement a foregone conclusion and measure
 * nothing. The stub instead implements a real, independent relevance function — IDF-weighted term
 * overlap normalised by passage length — computed only from the query and the passage text. It has
 * no access to {@code relevantIds}.
 *
 * <p>The baseline is raw term-overlap count, standing in for a lexical retriever: it favours longer
 * passages that share common words, which is the specific weakness a re-ranker should correct.
 *
 * <h3>The measured result is negative, and is reported as such</h3>
 * <p>On this dataset the stub re-ranker produces <em>no</em> improvement: mean nDCG@4 stays at
 * 0.431 and MRR@4 at 0.250. That is the honest outcome rather than a failure of the harness, and
 * it is informative. Every relevant passage here is written in the vocabulary of an <em>answer</em>
 * while its query is written as a <em>question</em>, so the correct passage shares few terms with
 * the query. A lexical scorer — however it weights those terms — has exactly the same blind spot as
 * the lexical baseline, and cannot repair a failure of meaning with a better count of words. The
 * number that matters is therefore the baseline: 0.431 is the bar a genuinely semantic re-ranker
 * (a real model behind {@link LlmRerankAdapter}, or a cross-encoder adapter) has to beat.
 *
 * <p>{@code harness_detectsAnImprovementWhenTheRerankerGenuinelyRanksBetter} exists to keep that
 * conclusion trustworthy: an oracle re-ranker drives nDCG@4 to 1.000 on the same data, proving the
 * instrument moves when ordering genuinely improves. Without that control, "no improvement" and
 * "broken measurement" would be indistinguishable.
 *
 * <h3>Running against a real model instead</h3>
 * <p>Replace the stub {@link LlmPort} with the Ollama-backed one and this class becomes a live
 * benchmark with no other change; replace {@link SyntheticEvaluationDataset} with human-labelled
 * data and it becomes a real quality measurement. That substitutability is the design goal — the
 * harness is meant to outlive the synthetic data it ships with.
 */
class RerankEvaluationBenchmarkTest {

    private static final int TOP_K = 4;

    @Test
    void benchmark_recordsBaselineAndRerankedMetricsOnTheSyntheticSet() {
        Aggregate baseline = new Aggregate();
        Aggregate reranked = new Aggregate();

        StringBuilder report = new StringBuilder("\n=== SYNTHETIC retrieval benchmark (engineering baseline, NOT production quality) ===\n");
        report.append(String.format("%-52s %9s %9s %9s%n", "query", "nDCG@4", "MRR@4", "Recall@4"));

        for (SyntheticEvaluationDataset.Case testCase : SyntheticEvaluationDataset.cases()) {
            List<RetrievedChunk> baselineOrder = baselineOrdering(testCase);
            List<String> baselineIds = idsOf(baselineOrder, testCase);

            LlmRerankAdapter adapter = new LlmRerankAdapter(idfWeightedStubScorer(testCase), 20);
            List<RetrievedChunk> rerankedOrder =
                    adapter.rerank(testCase.query(), baselineOrder, TOP_K);
            List<String> rerankedIds = idsOf(rerankedOrder, testCase);

            baseline.add(baselineIds, testCase.relevantIds());
            reranked.add(rerankedIds, testCase.relevantIds());

            report.append(String.format("%-52s %9.3f %9.3f %9.3f   (baseline)%n",
                    truncate(testCase.query()),
                    RetrievalMetrics.ndcgAtK(baselineIds, testCase.relevantIds(), TOP_K),
                    RetrievalMetrics.reciprocalRankAtK(baselineIds, testCase.relevantIds(), TOP_K),
                    RetrievalMetrics.recallAtK(baselineIds, testCase.relevantIds(), TOP_K)));
            report.append(String.format("%-52s %9.3f %9.3f %9.3f   (reranked)%n", "",
                    RetrievalMetrics.ndcgAtK(rerankedIds, testCase.relevantIds(), TOP_K),
                    RetrievalMetrics.reciprocalRankAtK(rerankedIds, testCase.relevantIds(), TOP_K),
                    RetrievalMetrics.recallAtK(rerankedIds, testCase.relevantIds(), TOP_K)));
        }

        report.append(String.format("%nMEAN  baseline: nDCG@4=%.3f  MRR@4=%.3f  Recall@4=%.3f%n",
                baseline.meanNdcg(), baseline.meanMrr(), baseline.meanRecall()));
        report.append(String.format("MEAN  reranked: nDCG@4=%.3f  MRR@4=%.3f  Recall@4=%.3f%n",
                reranked.meanNdcg(), reranked.meanMrr(), reranked.meanRecall()));
        System.out.println(report);

        // Recall cannot change: re-ranking reorders the candidates retrieval returned, it never
        // introduces or removes one. Asserting this guards against a re-ranker that silently drops
        // results — a correctness bug that a pure nDCG check would not catch.
        assertThat(reranked.meanRecall())
                .as("re-ranking must not lose candidates")
                .isEqualTo(baseline.meanRecall());

        assertThat(reranked.meanNdcg())
                .as("re-ranking must not degrade ordering on the synthetic set")
                .isGreaterThanOrEqualTo(baseline.meanNdcg());

        // Regression guard on the measured engineering baseline. Deliberately a floor rather than
        // an equality: a future change that genuinely improves ranking should not have to edit
        // this number, but one that quietly makes retrieval worse will fail here.
        assertThat(baseline.meanNdcg())
                .as("synthetic-set baseline nDCG@4 must not regress below the recorded value")
                .isGreaterThanOrEqualTo(0.43);
    }

    @Test
    void harness_detectsAnImprovementWhenTheRerankerGenuinelyRanksBetter() {
        // POSITIVE CONTROL — not a quality claim about Project EKA.
        //
        // The measurement above shows the stub scorer producing no improvement, which is the
        // honest result: the stub is lexical, and this dataset's relevant passages deliberately
        // share little vocabulary with their queries, so it has the same blind spot as the
        // lexical baseline. That leaves one question open — would this harness even notice an
        // improvement? This control answers it by scoring with an oracle that reads the relevance
        // labels. An oracle re-ranker is obviously not a real one; its only job is to prove the
        // harness and metrics move when ordering genuinely improves, so the negative result above
        // can be trusted as a measurement rather than a broken instrument.
        Aggregate baseline = new Aggregate();
        Aggregate oracle   = new Aggregate();

        for (SyntheticEvaluationDataset.Case testCase : SyntheticEvaluationDataset.cases()) {
            List<RetrievedChunk> baselineOrder = baselineOrdering(testCase);
            baseline.add(idsOf(baselineOrder, testCase), testCase.relevantIds());

            LlmRerankAdapter oracleAdapter = new LlmRerankAdapter(oracleScorer(testCase), 20);
            oracle.add(idsOf(oracleAdapter.rerank(testCase.query(), baselineOrder, TOP_K), testCase),
                    testCase.relevantIds());
        }

        assertThat(oracle.meanNdcg())
                .as("an oracle re-ranker must score perfectly, proving the harness detects improvement")
                .isEqualTo(1.0);
        assertThat(oracle.meanNdcg()).isGreaterThan(baseline.meanNdcg());
    }

    /** Scores by the relevance labels. Used only as a positive control; never as a measurement. */
    private LlmPort oracleScorer(SyntheticEvaluationDataset.Case testCase) {
        Map<String, String> textToId = new HashMap<>();
        for (SyntheticEvaluationDataset.Passage passage : testCase.passages()) {
            textToId.put(passage.text(), passage.id());
        }
        return request -> {
            String passageText = request.promptRequest().userText();
            String id = textToId.entrySet().stream()
                    .filter(e -> passageText.contains(e.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst().orElse("");
            double score = testCase.relevantIds().contains(id) ? 1.0 : 0.0;
            return new LlmResponse(String.format("%.1f", score), FinishReason.STOP, "oracle", 0, 0, 0L);
        };
    }

    @Test
    void aTotallyUnavailableModelLeavesTheBaselineOrderingUntouched() {
        // The degradation contract from RerankPort's Javadoc, measured rather than asserted by
        // inspection: if every scoring call throws, the result must equal the input ordering.
        LlmPort brokenModel = request -> {
            throw new IllegalStateException("model unavailable");
        };
        LlmRerankAdapter adapter = new LlmRerankAdapter(brokenModel, 20);

        for (SyntheticEvaluationDataset.Case testCase : SyntheticEvaluationDataset.cases()) {
            List<RetrievedChunk> baselineOrder = baselineOrdering(testCase);

            List<String> before = idsOf(baselineOrder, testCase);
            List<String> after  = idsOf(adapter.rerank(testCase.query(), baselineOrder, TOP_K), testCase);

            assertThat(after).isEqualTo(before.subList(0, Math.min(TOP_K, before.size())));
        }
    }

    // ── Baseline retriever stand-in ───────────────────────────────────────────

    /** Raw term-overlap count: a deliberately weak lexical retriever, longest-match-wins. */
    private List<RetrievedChunk> baselineOrdering(SyntheticEvaluationDataset.Case testCase) {
        Set<String> queryTerms = terms(testCase.query());

        List<SyntheticEvaluationDataset.Passage> ordered = new ArrayList<>(testCase.passages());
        ordered.sort(Comparator
                .comparingLong((SyntheticEvaluationDataset.Passage p) ->
                        terms(p.text()).stream().filter(queryTerms::contains).count())
                .reversed()
                .thenComparing(SyntheticEvaluationDataset.Passage::id));

        List<RetrievedChunk> chunks = new ArrayList<>(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            SyntheticEvaluationDataset.Passage passage = ordered.get(i);
            chunks.add(new RetrievedChunk(
                    ChunkId.of(chunkUuidFor(passage.id())), DocumentId.generate(), TenantId.generate(),
                    passage.text(), 1.0 - (i * 0.01), i));
        }
        return chunks;
    }

    // ── Stub scorer standing in for a real re-ranking model ───────────────────

    /**
     * IDF-weighted overlap normalised by passage length, computed purely from the query and the
     * passage text. Has no access to the relevance labels.
     */
    private LlmPort idfWeightedStubScorer(SyntheticEvaluationDataset.Case testCase) {
        Map<String, Double> idf = inverseDocumentFrequencies(testCase);
        Set<String> queryTerms = terms(testCase.query());

        double best = testCase.passages().stream()
                .mapToDouble(p -> weightedOverlap(queryTerms, p.text(), idf))
                .max().orElse(1.0);
        double normaliser = best > 0 ? best : 1.0;

        return request -> {
            String passageText = request.promptRequest().userText();
            double score = Math.min(1.0, weightedOverlap(queryTerms, passageText, idf) / normaliser);
            return new LlmResponse(String.format("%.4f", score), FinishReason.STOP, "stub", 0, 0, 0L);
        };
    }

    private double weightedOverlap(Set<String> queryTerms, String passageText, Map<String, Double> idf) {
        List<String> passageTerms = new ArrayList<>(terms(passageText));
        if (passageTerms.isEmpty()) return 0.0;

        double score = 0.0;
        for (String term : new HashSet<>(passageTerms)) {
            if (queryTerms.contains(term)) {
                score += idf.getOrDefault(term, 1.0);
            }
        }
        // Length normalisation: without it, a long passage that mentions many query words in
        // passing outranks a short one that actually answers the question.
        return score / Math.sqrt(passageTerms.size());
    }

    private Map<String, Double> inverseDocumentFrequencies(SyntheticEvaluationDataset.Case testCase) {
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (SyntheticEvaluationDataset.Passage passage : testCase.passages()) {
            for (String term : terms(passage.text())) {
                documentFrequency.merge(term, 1, Integer::sum);
            }
        }
        int total = testCase.passages().size();
        Map<String, Double> idf = new HashMap<>();
        documentFrequency.forEach((term, count) ->
                idf.put(term, Math.log((double) (total + 1) / (count + 1)) + 1.0));
        return idf;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "is", "are", "to", "of", "for", "and", "or", "in", "on", "do", "does",
            "how", "what", "can", "be", "by", "at", "as", "from", "with", "within", "their", "have");

    private Set<String> terms(String text) {
        Set<String> result = new HashSet<>();
        for (String raw : text.toLowerCase().split("[^a-z0-9]+")) {
            if (!raw.isBlank() && !STOP_WORDS.contains(raw)) {
                result.add(raw);
            }
        }
        return result;
    }

    /** Stable id↔UUID mapping so metrics can compare passage ids, not UUIDs. */
    private UUID chunkUuidFor(String passageId) {
        return UUID.nameUUIDFromBytes(passageId.getBytes());
    }

    private List<String> idsOf(List<RetrievedChunk> chunks, SyntheticEvaluationDataset.Case testCase) {
        Map<UUID, String> idByUuid = new HashMap<>();
        for (SyntheticEvaluationDataset.Passage passage : testCase.passages()) {
            idByUuid.put(chunkUuidFor(passage.id()), passage.id());
        }
        return chunks.stream().map(c -> idByUuid.get(c.chunkId().value())).toList();
    }

    private String truncate(String query) {
        return query.length() <= 50 ? query : query.substring(0, 47) + "...";
    }

    private static final class Aggregate {
        private final List<Double> ndcg   = new ArrayList<>();
        private final List<Double> mrr    = new ArrayList<>();
        private final List<Double> recall = new ArrayList<>();

        void add(List<String> rankedIds, Set<String> relevantIds) {
            ndcg.add(RetrievalMetrics.ndcgAtK(rankedIds, relevantIds, TOP_K));
            mrr.add(RetrievalMetrics.reciprocalRankAtK(rankedIds, relevantIds, TOP_K));
            recall.add(RetrievalMetrics.recallAtK(rankedIds, relevantIds, TOP_K));
        }

        double meanNdcg()   { return mean(ndcg); }
        double meanMrr()    { return mean(mrr); }
        double meanRecall() { return mean(recall); }

        private double mean(List<Double> values) {
            return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }
    }
}
