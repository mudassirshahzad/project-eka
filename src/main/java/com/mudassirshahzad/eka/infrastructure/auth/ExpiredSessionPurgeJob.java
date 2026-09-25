package com.mudassirshahzad.eka.infrastructure.auth;

import com.mudassirshahzad.eka.application.auth.SessionApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes sessions whose refresh token has already expired, bounding unbounded growth of
 * {@code refresh_tokens} on a long-running deployment.
 *
 * <p>Purely housekeeping, never a security control: an expired session is already rejected by
 * {@code RefreshToken.isActive}, so a row that outlives its expiry grants nothing. That ordering
 * matters — if this job never ran, the system would waste storage, not authentication.
 *
 * <p>Follows the same shape as {@code UnclassifiedDocumentStartupCheck}: a small technical
 * component in {@code infrastructure} that drives one already-tested application-service method,
 * containing no policy of its own. {@code @EnableScheduling} is already on
 * {@code ProjectEkaApplication} (v0.6.1, ADR EX05) — no new enabling configuration is introduced.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiredSessionPurgeJob {

    private final SessionApplicationService sessionApplicationService;

    @Scheduled(
            initialDelayString = "${app.session.purge-initial-delay-ms:3600000}",
            fixedDelayString   = "${app.session.purge-interval-ms:3600000}")
    public void purgeExpiredSessions() {
        try {
            int deleted = sessionApplicationService.purgeExpiredSessions();
            if (deleted > 0) {
                log.info("Purged {} expired session(s)", deleted);
            }
        } catch (RuntimeException ex) {
            // A failed purge must never take the scheduler thread down: the next run retries, and
            // nothing about authentication depends on this having succeeded.
            log.warn("Expired-session purge failed; will retry on the next run: {}",
                    ex.getClass().getSimpleName());
        }
    }
}
