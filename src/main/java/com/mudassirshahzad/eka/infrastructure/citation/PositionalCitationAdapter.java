package com.mudassirshahzad.eka.infrastructure.citation;

import com.mudassirshahzad.eka.domain.chunk.ChunkId;
import com.mudassirshahzad.eka.domain.conversation.Citation;
import com.mudassirshahzad.eka.domain.generation.port.CitationPort;
import com.mudassirshahzad.eka.domain.retrieval.model.AssembledChunk;
import com.mudassirshahzad.eka.domain.retrieval.model.AssembledContext;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Production {@link CitationPort} implementation that resolves {@code [SOURCE:N]} markers
 * emitted by the LLM (per ADR G03, {@code TemplateBasedPromptBuilderAdapter}) back into
 * {@link Citation} objects against the {@link AssembledChunk}s that were actually offered to it.
 *
 * <h3>Parsing strategy</h3>
 * <p>Markers are located with a hand-written left-to-right scan for the literal prefix
 * {@code "[SOURCE:"} and the next {@code ']'} — no regular expression is used, so there is no
 * risk of catastrophic backtracking on adversarial or malformed LLM output. A candidate marker
 * is accepted only when the text between the prefix and the bracket is one or more digits
 * (ADR G03: markers are 1-based); anything else — empty index, non-digit characters, a missing
 * closing bracket — is skipped and scanning resumes after the failure point. A single malformed
 * marker therefore never prevents later, well-formed markers in the same response from resolving.
 *
 * <h3>Resolution</h3>
 * <p>Each accepted marker index is resolved against {@link AssembledChunk#position()} — not
 * against list order — so resolution stays correct even if a future context assembly
 * implementation does not return chunks pre-sorted by position. Indexes with no matching chunk
 * (out of range, or referencing a chunk that was never assembled) are silently ignored.
 *
 * <h3>Ordering and duplicates</h3>
 * <p>Citations are returned in first-appearance order. Repeated markers for the same chunk
 * (e.g. {@code [SOURCE:1] ... [SOURCE:1]}) contribute a single {@link Citation} — a citation
 * list reflects distinct sources referenced, not how many times each was mentioned.
 *
 * <h3>Failure tolerance</h3>
 * <p>This adapter never throws for malformed, missing, or out-of-range references. Generation
 * must always complete; citation resolution degrades gracefully instead of failing the request.
 */
@Slf4j
@Component
public class PositionalCitationAdapter implements CitationPort {

    static final String MARKER_PREFIX = "[SOURCE:";
    static final char   MARKER_SUFFIX = ']';

    @Override
    public List<Citation> resolve(String generatedText, AssembledContext context, TenantId tenantId) {
        Objects.requireNonNull(generatedText, "generatedText must not be null");
        Objects.requireNonNull(context,       "context must not be null");
        Objects.requireNonNull(tenantId,      "tenantId must not be null");

        Map<Integer, AssembledChunk> chunksByPosition = indexByPosition(context);

        List<Citation> citations = new ArrayList<>();
        Set<ChunkId>    seen     = new HashSet<>();

        for (int chunkPosition : parseMarkerPositions(generatedText)) {
            AssembledChunk chunk = chunksByPosition.get(chunkPosition);
            if (chunk != null && seen.add(chunk.chunkId())) {
                citations.add(Citation.of(chunk.chunkId(), chunk.score()));
            }
        }

        log.debug("Citation resolution: tenant={} chunks={} citationsResolved={}",
                tenantId, context.size(), citations.size());

        return List.copyOf(citations);
    }

    private static Map<Integer, AssembledChunk> indexByPosition(AssembledContext context) {
        Map<Integer, AssembledChunk> byPosition = new HashMap<>();
        for (AssembledChunk chunk : context.chunks()) {
            byPosition.put(chunk.position(), chunk);
        }
        return byPosition;
    }

    /**
     * Scans {@code text} for {@code [SOURCE:N]} markers and returns the zero-based chunk
     * position ({@code N - 1}) for each syntactically valid marker, in the order encountered.
     */
    private static List<Integer> parseMarkerPositions(String text) {
        List<Integer> positions  = new ArrayList<>();
        int            searchFrom = 0;

        while (searchFrom < text.length()) {
            int prefixStart = text.indexOf(MARKER_PREFIX, searchFrom);
            if (prefixStart < 0) {
                break;
            }

            int digitsStart   = prefixStart + MARKER_PREFIX.length();
            int closeBracket  = text.indexOf(MARKER_SUFFIX, digitsStart);
            if (closeBracket < 0) {
                break;
            }

            String candidate = text.substring(digitsStart, closeBracket);
            searchFrom = closeBracket + 1;

            parseOneBasedIndex(candidate).ifPresent(n -> positions.add(n - 1));
        }
        return positions;
    }

    private static OptionalInt parseOneBasedIndex(String candidate) {
        if (candidate.isEmpty() || !isAllDigits(candidate)) {
            return OptionalInt.empty();
        }
        try {
            int n = Integer.parseInt(candidate);
            return n >= 1 ? OptionalInt.of(n) : OptionalInt.empty();
        } catch (NumberFormatException tooManyDigits) {
            return OptionalInt.empty();
        }
    }

    private static boolean isAllDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
