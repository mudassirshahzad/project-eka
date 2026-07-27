package com.mudassir.eka.infrastructure.citation;

import com.mudassir.eka.domain.conversation.Citation;
import com.mudassir.eka.domain.generation.port.CitationPort;
import com.mudassir.eka.domain.retrieval.model.AssembledContext;
import com.mudassir.eka.domain.shared.TenantId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Named seam implementation of {@link CitationPort} that returns an empty citation list.
 *
 * <p>This adapter is an explicit architectural placeholder. Full citation resolution —
 * parsing {@code [SOURCE:N]} markers from the generated text and mapping them to
 * {@link Citation} objects from the {@link AssembledContext} — is the P04.11 milestone
 * ({@code PositionalCitationAdapter}). Until then, this stub ensures the port is wired end-to-end
 * and callers receive a valid (empty) citation list rather than null.
 */
@Slf4j
@Component
public class PassthroughCitationAdapter implements CitationPort {

    @Override
    public List<Citation> resolve(String generatedText, AssembledContext context, TenantId tenantId) {
        Objects.requireNonNull(generatedText, "generatedText must not be null");
        Objects.requireNonNull(context,       "context must not be null");
        Objects.requireNonNull(tenantId,      "tenantId must not be null");

        log.debug("Citation resolution (passthrough): tenant={} chunks={}", tenantId, context.size());
        return List.of();
    }
}
