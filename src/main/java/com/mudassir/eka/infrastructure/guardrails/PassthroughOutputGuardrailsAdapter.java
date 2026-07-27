package com.mudassir.eka.infrastructure.guardrails;

import com.mudassir.eka.domain.generation.model.GuardrailResult;
import com.mudassir.eka.domain.generation.port.OutputGuardrailsPort;
import com.mudassir.eka.domain.shared.TenantId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Named seam implementation of {@link OutputGuardrailsPort} that passes all text through without
 * modification.
 *
 * <p>This adapter is an explicit architectural placeholder — not a missing feature. It marks the
 * boundary where output content safety enforcement will be wired in at a future milestone.
 * By existing as a named component, it ensures the port is exercised end-to-end today and can be
 * replaced with a real implementation (e.g. an LLM-based classifier or a rule-based filter) without
 * changing any port signature or caller.
 *
 * <p>Always returns {@link GuardrailResult#pass(String)} with the original text.
 */
@Slf4j
@Component
public class PassthroughOutputGuardrailsAdapter implements OutputGuardrailsPort {

    @Override
    public GuardrailResult apply(String generatedText, TenantId tenantId) {
        Objects.requireNonNull(generatedText, "generatedText must not be null");
        Objects.requireNonNull(tenantId,      "tenantId must not be null");

        log.debug("Output guardrails (passthrough): tenant={} textLength={}", tenantId, generatedText.length());
        return GuardrailResult.pass(generatedText);
    }
}
