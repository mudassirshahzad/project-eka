package com.mudassirshahzad.eka.infrastructure.reconciliation;

import com.mudassirshahzad.eka.application.reconciliation.ChunkReconciliationService;
import com.mudassirshahzad.eka.application.reconciliation.ReconciliationResult;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Drives {@link ChunkReconciliationService} on a schedule and exposes the signals an operator
 * needs to alert on (WP-3, ADR OR02/OR03).
 *
 * <h3>Alerting is a gauge, not just a log line</h3>
 * <p>Phase 7's exit criterion is a reconciliation job "running on a schedule **with alerting**, not
 * passive logging". Counters alone cannot express "this job has stopped running", which is the
 * failure that hides every other one — a job that never runs reports no drift, exactly like a
 * healthy system. {@code eka.reconciliation.seconds_since_last_success} therefore grows
 * monotonically until a pass completes, so a single alert rule
 * ({@code seconds_since_last_success > 2 × interval}) catches both a stalled scheduler and a
 * dependency outage, and {@code eka.reconciliation.failed} catches drift that could not be repaired.
 *
 * <p>Follows the {@code ExpiredSessionPurgeJob}/{@code UnclassifiedDocumentStartupCheck} shape: a
 * thin technical driver in {@code infrastructure} around one already-tested application method,
 * holding no policy of its own. {@code @EnableScheduling} is already present on
 * {@code ProjectEkaApplication} (v0.6.1, ADR EX05).
 */
@Slf4j
@Component
public class ReconciliationJob {

    private final ChunkReconciliationService reconciliationService;
    private final AtomicLong                 lastSuccessEpochMs = new AtomicLong(System.currentTimeMillis());

    public ReconciliationJob(ChunkReconciliationService reconciliationService,
                             MeterRegistry meterRegistry) {
        this.reconciliationService = reconciliationService;

        meterRegistry.gauge("eka.reconciliation.seconds_since_last_success", this,
                job -> (System.currentTimeMillis() - job.lastSuccessEpochMs.get()) / 1000.0);
    }

    @Scheduled(
            initialDelayString = "${app.reconciliation.initial-delay-ms:300000}",
            fixedDelayString   = "${app.reconciliation.interval-ms:900000}")
    public void run() {
        try {
            ReconciliationResult result = reconciliationService.reconcile();
            // Only a completed pass counts as success. A pass that repaired nothing because there
            // was nothing to repair is still a healthy, working job.
            lastSuccessEpochMs.set(System.currentTimeMillis());

            if (result.hasUnrepairedDrift()) {
                log.error("Reconciliation finished with unrepaired drift: {}", result);
            }
        } catch (RuntimeException ex) {
            // Deliberately does not update lastSuccessEpochMs: the gauge keeps climbing, which is
            // what makes a persistently failing job visible instead of silently absent.
            log.error("Reconciliation pass failed: {}", ex.getClass().getSimpleName(), ex);
        }
    }
}
