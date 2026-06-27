package com.mudassir.eka.application.document;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SlidingWindowChunkingStrategy implements ChunkingStrategy {

    private static final Pattern TOKEN_PATTERN     = Pattern.compile("[^\\s]+");
    private static final Pattern PARAGRAPH_PATTERN = Pattern.compile("\\n{2,}|\\r\\n\\r\\n");

    private final int chunkSize;
    private final int chunkOverlap;

    public SlidingWindowChunkingStrategy(
            @Value("${app.ingestion.chunk-size:512}")    int chunkSize,
            @Value("${app.ingestion.chunk-overlap:64}")  int chunkOverlap
    ) {
        if (chunkSize < 1)              throw new IllegalArgumentException("chunkSize must be >= 1");
        if (chunkOverlap < 0)           throw new IllegalArgumentException("chunkOverlap must be >= 0");
        if (chunkOverlap >= chunkSize)  throw new IllegalArgumentException("chunkOverlap must be < chunkSize");
        this.chunkSize    = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    @Override
    public String name() { return "sliding-window"; }

    @Override
    public List<TextSegment> chunk(String text) {
        if (text == null || text.isBlank()) return List.of();

        List<int[]> tokens = collectTokens(text);
        if (tokens.isEmpty()) return List.of();

        List<Integer> paragraphBreaks = collectParagraphBreaks(text);
        List<TextSegment> segments    = new ArrayList<>();
        int windowStart = 0;

        while (windowStart < tokens.size()) {
            int windowEnd = Math.min(windowStart + chunkSize, tokens.size());

            // Snap non-final windows backward to a paragraph boundary when possible.
            // Requires the snap still guarantees forward progress past the overlap.
            if (windowEnd < tokens.size()) {
                int minWindowEnd = windowStart + chunkOverlap + 1;
                for (int i = paragraphBreaks.size() - 1; i >= 0; i--) {
                    int snapEnd = lastTokenEndBefore(tokens, paragraphBreaks.get(i));
                    if (snapEnd >= minWindowEnd && snapEnd < windowEnd) {
                        windowEnd = snapEnd;
                        break;
                    }
                }
            }

            int startChar = tokens.get(windowStart)[0];
            int endChar   = tokens.get(windowEnd - 1)[1];
            segments.add(new TextSegment(
                    text.substring(startChar, endChar),
                    startChar,
                    endChar,
                    windowEnd - windowStart,
                    segments.size()
            ));

            // Stop when all tokens have been included in this window
            if (windowEnd == tokens.size()) break;

            int nextStart = windowEnd - chunkOverlap;
            windowStart = Math.max(nextStart, windowStart + 1); // guarantee forward progress
        }

        return List.copyOf(segments);
    }

    // Returns the exclusive token index of the last token ending at or before charPos.
    private int lastTokenEndBefore(List<int[]> tokens, int charPos) {
        for (int i = tokens.size() - 1; i >= 0; i--) {
            if (tokens.get(i)[1] <= charPos) {
                return i + 1;
            }
        }
        return 0;
    }

    private List<int[]> collectTokens(String text) {
        List<int[]> tokens = new ArrayList<>();
        Matcher m = TOKEN_PATTERN.matcher(text);
        while (m.find()) {
            tokens.add(new int[]{m.start(), m.end()});
        }
        return tokens;
    }

    private List<Integer> collectParagraphBreaks(String text) {
        List<Integer> breaks = new ArrayList<>();
        Matcher m = PARAGRAPH_PATTERN.matcher(text);
        while (m.find()) {
            breaks.add(m.end());
        }
        return breaks;
    }
}
