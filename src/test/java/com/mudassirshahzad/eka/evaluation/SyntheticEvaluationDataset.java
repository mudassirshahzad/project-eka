package com.mudassirshahzad.eka.evaluation;

import java.util.List;
import java.util.Set;

/**
 * A small, deliberately <strong>SYNTHETIC</strong> retrieval evaluation set (WP-4, ADR RQ05).
 *
 * <h3>Read this before drawing any conclusion from a number produced with it</h3>
 * <p>These queries, passages, and relevance labels were authored alongside the code they evaluate.
 * They are <em>not</em> production ground truth, and a score measured here says nothing about how
 * well Project EKA retrieves over a real corpus. What this dataset provides is an <em>engineering
 * baseline</em>: a fixed, deterministic yardstick that makes a retrieval-quality regression visible
 * in CI, and gives the harness something to exercise before real labelled data exists.
 *
 * <h3>Why synthetic data is still worth having</h3>
 * <p>Without it, {@link RetrievalMetricsTest}-style assertions could only check arithmetic, and the
 * first real evaluation would arrive with an unexercised harness. With it, the harness, the metric
 * definitions, and the comparison methodology are all proven, so a future human-labelled dataset
 * can replace this file and nothing else — that replaceability is the whole design goal.
 *
 * <h3>Construction</h3>
 * <p>Each query has exactly one or two genuinely relevant passages plus several distractors that
 * share vocabulary with the query but do not answer it. The distractors matter more than the
 * relevant passages: a lexical retriever that matches on shared terms will rank them highly, which
 * is precisely the failure a re-ranker should correct.
 */
public final class SyntheticEvaluationDataset {

    private SyntheticEvaluationDataset() {}

    /** One evaluation case: a query, the passages available, and which of them actually answer it. */
    public record Case(String query, List<Passage> passages, Set<String> relevantIds) {}

    /** A candidate passage. {@code id} is what metrics compare; {@code text} is what a re-ranker reads. */
    public record Passage(String id, String text) {}

    public static List<Case> cases() {
        return List.of(
                new Case(
                        "how long do employees have to submit expense claims?",
                        List.of(
                                // Answers the question, but shares few query words with it.
                                new Passage("d1", "Claims must be filed no later than 60 days after the "
                                        + "date incurred. Anything later needs director sign-off."),
                                // Distractors deliberately repeat the query's vocabulary without
                                // answering it, so a raw term-overlap retriever ranks them first.
                                new Passage("d2", "Employees submit expense claims through the expenses "
                                        + "portal. Employees who submit expense claims incorrectly are "
                                        + "asked to resubmit the expense claim."),
                                new Passage("d3", "Expense claims submitted by employees are reviewed by "
                                        + "the expenses team. Employees may submit claims for travel."),
                                new Passage("d4", "Long-serving employees may submit expense claims for "
                                        + "professional subscriptions, provided the expense is approved.")),
                        Set.of("d1")),

                new Case(
                        "what is the notice period for resignation?",
                        List.of(
                                new Passage("e1", "A permanent member of staff who resigns must give one "
                                        + "calendar month in writing; senior grades give three months."),
                                new Passage("e2", "Notice of scheduled maintenance is published to the "
                                        + "status page. This notice period is 48 hours for any outage, "
                                        + "and notice is repeated on the day."),
                                new Passage("e3", "The resignation process is described in the handbook. "
                                        + "Resignation letters go to the people team, who acknowledge "
                                        + "each resignation within two days."),
                                new Passage("e4", "Probation period reviews occur at three and six months. "
                                        + "The probation period may be extended once.")),
                        Set.of("e1")),

                new Case(
                        "can contractors access the production database?",
                        List.of(
                                new Passage("f1", "Only named platform-team staff hold credentials for the "
                                        + "live datastore. External personnel are never granted them."),
                                new Passage("f2", "Contractors can access the staging database and the "
                                        + "contractor documentation portal. Contractor access to staging "
                                        + "is approved by the service desk."),
                                new Passage("f3", "The production database is backed up nightly. Production "
                                        + "database restores are tested weekly against the production "
                                        + "backup catalogue."),
                                new Passage("f4", "Database access requests, including production database "
                                        + "access, are logged. Contractors raising an access request must "
                                        + "supply a sponsor.")),
                        Set.of("f1")),

                new Case(
                        "how often are access reviews performed?",
                        List.of(
                                new Passage("g1", "System owners must confirm or revoke every account once "
                                        + "per quarter, within ten working days of the cycle opening."),
                                new Passage("g2", "Access reviews are performed by system owners. An access "
                                        + "review covers every account, and access review findings are "
                                        + "recorded in the access review log."),
                                new Passage("g3", "Performance reviews are performed twice a year. Review "
                                        + "outcomes are recorded and reviews are signed off by managers."),
                                new Passage("g4", "Badge access to the building is reviewed by facilities. "
                                        + "Access records are retained for one year.")),
                        Set.of("g1")));
    }
}
