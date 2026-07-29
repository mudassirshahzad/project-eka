package com.mudassirshahzad.eka.infrastructure.guardrails;

import com.mudassirshahzad.eka.domain.generation.model.GuardrailResult;
import com.mudassirshahzad.eka.domain.generation.port.OutputGuardrailsPort;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Production {@link OutputGuardrailsPort} implementation enforcing a deterministic, provider-
 * independent policy layer over LLM output — not an AI moderation or classification engine.
 *
 * <h3>Policy</h3>
 * <ol>
 *   <li><b>Absent or blank content</b> — a {@code null}, empty, or whitespace-only response
 *       (including one that becomes whitespace-only after control-character stripping) is a
 *       policy violation, resolved in-band as {@link GuardrailResult#block(String)} with
 *       {@link #SAFE_FALLBACK_TEXT}. This adapter never throws for malformed output — a missing
 *       or empty LLM response is exactly the kind of provider misbehavior guardrails exist to
 *       absorb (ADR GR02).</li>
 *   <li><b>Malformed / non-printable content</b> — Unicode control characters other than
 *       {@code \r}, {@code \n}, {@code \t} are stripped before any other check runs
 *       (ADR GR04).</li>
 *   <li><b>Oversized content</b> — text exceeding {@code app.guardrails.max-response-length}
 *       (default 8192 characters) is truncated to that limit and passed, not blocked
 *       (ADR GR03).</li>
 * </ol>
 *
 * <p>Finish-reason-aware blocking (e.g. treating {@code FinishReason.ERROR} as a policy
 * violation) is intentionally out of scope: {@link OutputGuardrailsPort#apply(String, TenantId)}
 * receives text only per the frozen ADR G16, and this milestone does not evolve that signature
 * (ADR GR01). It is documented technical debt, not an oversight.
 *
 * <p>{@code tenantId} remains a required precondition — {@code null} throws
 * {@link NullPointerException}, consistent with every other port in this codebase. Only
 * {@code generatedText} is treated as untrusted provider output subject to policy, not a caller
 * contract.
 *
 * <p>Never logs generated text content — only tenant, length, and the policy outcome, matching
 * the project-wide logging policy.
 */
@Slf4j
@Component
public class PolicyBasedOutputGuardrailsAdapter implements OutputGuardrailsPort {

    static final String SAFE_FALLBACK_TEXT =
            "The response could not be generated safely. Please try again.";

    /** Control characters other than CR, LF, and TAB — stripped as malformed content. */
    private static final String CONTROL_CHARACTERS_EXCEPT_WHITESPACE = "[\\p{Cntrl}&&[^\r\n\t]]";

    private final int maxResponseLength;

    public PolicyBasedOutputGuardrailsAdapter(
            @Value("${app.guardrails.max-response-length:8192}") int maxResponseLength) {
        if (maxResponseLength < 1) {
            throw new IllegalArgumentException(
                    "maxResponseLength must be >= 1 but was " + maxResponseLength);
        }
        this.maxResponseLength = maxResponseLength;
    }

    @Override
    public GuardrailResult apply(String generatedText, TenantId tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        if (generatedText == null) {
            log.warn("Output guardrails blocked response: tenant={} reason=null", tenantId);
            return GuardrailResult.block(SAFE_FALLBACK_TEXT);
        }

        String normalized = generatedText
                .replaceAll(CONTROL_CHARACTERS_EXCEPT_WHITESPACE, "")
                .trim();

        if (normalized.isBlank()) {
            log.warn("Output guardrails blocked response: tenant={} reason=blank", tenantId);
            return GuardrailResult.block(SAFE_FALLBACK_TEXT);
        }

        if (normalized.length() > maxResponseLength) {
            log.debug("Output guardrails truncated response: tenant={} originalLength={} maxLength={}",
                    tenantId, normalized.length(), maxResponseLength);
            normalized = normalized.substring(0, maxResponseLength);
        }

        log.debug("Output guardrails passed response: tenant={} textLength={}", tenantId, normalized.length());
        return GuardrailResult.pass(normalized);
    }
}
