package com.mudassirshahzad.eka.domain.document;

import java.util.Arrays;
import java.util.Optional;

/**
 * Sensitivity tier a document is classified at. Comparison for authorization purposes must
 * always go through {@link #level()}, never {@link Enum#ordinal()} — {@code level} is an explicit,
 * stable numeric property so a future tier can be inserted without silently renumbering every
 * tier below it (unlike ordinal, which is defined purely by declaration order).
 */
public enum DocumentClassification {

    PUBLIC(0),
    INTERNAL(1),
    CONFIDENTIAL(2),
    RESTRICTED(3);

    /**
     * Sentinel level for a classification value that does not parse to any known tier (a
     * malformed or unrecognized stored string). Deliberately higher than every real tier's level
     * so a {@code level <= maxClearance} comparison can never be satisfied, regardless of the
     * caller's clearance — unknown classification must never widen access.
     */
    public static final int UNKNOWN_LEVEL = Integer.MAX_VALUE;

    private final int level;

    DocumentClassification(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }

    /**
     * Parses a stored classification string into its tier. Returns empty for {@code null},
     * blank, or any value that isn't one of this enum's names — callers must treat "empty" as
     * {@link #UNKNOWN_LEVEL}, never as a specific tier or as unrestricted access.
     */
    public static Optional<DocumentClassification> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(c -> c.name().equalsIgnoreCase(value.trim()))
                .findFirst();
    }

    /**
     * Resolves the numeric level for a stored classification string, mapping anything that
     * doesn't parse (including {@code null}) to {@link #UNKNOWN_LEVEL}. The single place this
     * fail-closed mapping is defined — every enforcement site (retrieval, REST document
     * endpoints, backfill verification) must go through this, not re-implement it.
     *
     * <p>This is the fail-closed, <em>post</em>-backfill rule (P06.2 decision: "after migration
     * completes, null classifications become invalid and are denied by default"). There is no
     * separate "pre-backfill, null means INTERNAL" code path: the {@code V018} backfill migration
     * and this enforcement code ship in the same release, and Flyway migrations run to completion
     * at application startup before any request is served — so by the time this method can ever
     * be reached by a real caller, {@code V018} has already run and no legitimately-ingested
     * document is null any more. A null this method still sees after that means a document was
     * written outside {@code UploadDocumentUseCase} (bypassing the now-mandatory classification
     * requirement) — correctly deny-by-default, not a transition state to special-case.
     */
    public static int levelOf(String value) {
        return parse(value).map(DocumentClassification::level).orElse(UNKNOWN_LEVEL);
    }
}
