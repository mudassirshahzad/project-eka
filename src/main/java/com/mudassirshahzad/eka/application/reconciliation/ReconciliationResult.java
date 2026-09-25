package com.mudassirshahzad.eka.application.reconciliation;

/**
 * Outcome of one reconciliation pass.
 *
 * <p>{@code detected} counts chunks found un-indexed; {@code repaired} counts those successfully
 * re-indexed; {@code failed} counts those whose repair attempt threw. They are reported separately
 * rather than collapsed into a success flag because "drift exists" and "drift could not be fixed"
 * warrant different operational responses — the first is routine self-healing, the second means a
 * dependency is down and a human should know.
 */
public record ReconciliationResult(int detected, int repaired, int failed) {

    public static ReconciliationResult nothingToDo() {
        return new ReconciliationResult(0, 0, 0);
    }

    public boolean foundDrift() {
        return detected > 0;
    }

    public boolean hasUnrepairedDrift() {
        return failed > 0;
    }
}
