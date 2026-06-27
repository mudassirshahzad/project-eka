package com.mudassir.eka.application.document;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SlidingWindowChunkingStrategyTest {

    private SlidingWindowChunkingStrategy strategy(int chunkSize, int overlap) {
        return new SlidingWindowChunkingStrategy(chunkSize, overlap);
    }

    @Test
    void chunk_returnsEmptyForNull() {
        assertThat(strategy(10, 2).chunk(null)).isEmpty();
    }

    @Test
    void chunk_returnsEmptyForBlankText() {
        assertThat(strategy(10, 2).chunk("   \n  ")).isEmpty();
    }

    @Test
    void chunk_singleChunk_whenTextFitsInWindow() {
        String text = "one two three four five";
        List<TextSegment> segments = strategy(10, 2).chunk(text);
        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).content()).isEqualTo(text);
        assertThat(segments.get(0).sequenceNumber()).isZero();
        assertThat(segments.get(0).tokenCount()).isEqualTo(5);
    }

    @Test
    void chunk_multipleChunks_withOverlap() {
        // 10 tokens, chunkSize=4, overlap=2 → tokens [0..3], [2..5], [4..7], [6..9]
        String text = "a b c d e f g h i j";
        List<TextSegment> segments = strategy(4, 2).chunk(text);
        assertThat(segments.size()).isGreaterThan(1);
        // Verify overlap: last token(s) of segment N appear at the start of segment N+1
        for (int i = 0; i < segments.size() - 1; i++) {
            assertThat(segments.get(i + 1).startOffset())
                    .isLessThan(segments.get(i).endOffset());
        }
    }

    @Test
    void chunk_sequenceNumbersAreZeroIndexedAndMonotonic() {
        String text = "a b c d e f g h i j";
        List<TextSegment> segments = strategy(4, 1).chunk(text);
        for (int i = 0; i < segments.size(); i++) {
            assertThat(segments.get(i).sequenceNumber()).isEqualTo(i);
        }
    }

    @Test
    void chunk_offsetsMatchActualCharacterPositions() {
        String text = "hello world foo bar";
        List<TextSegment> segments = strategy(10, 1).chunk(text);
        for (TextSegment seg : segments) {
            String extracted = text.substring(seg.startOffset(), seg.endOffset());
            assertThat(extracted).isEqualTo(seg.content());
        }
    }

    @Test
    void chunk_snapsToParBoundaryWithinWindow() {
        // paragraph break after "alpha beta" — a 2-token paragraph — with chunkSize=5, overlap=1
        // Should end chunk at boundary instead of mid-paragraph
        String text = "alpha beta\n\ngamma delta epsilon zeta eta";
        List<TextSegment> segments = strategy(5, 1).chunk(text);
        // First segment should end at "beta" (boundary snap), not include gamma
        assertThat(segments.get(0).content()).doesNotContain("gamma");
    }

    @Test
    void chunk_guaranteesProgressWhenOverlapNearChunkSize() {
        // chunkSize=3, overlap=2 → each window advances by at least 1 token
        String text = "a b c d e f";
        List<TextSegment> segments = strategy(3, 2).chunk(text);
        // Should terminate (no infinite loop) and produce multiple segments
        assertThat(segments).hasSizeGreaterThan(1);
        // Verify monotonically increasing start offsets
        for (int i = 1; i < segments.size(); i++) {
            assertThat(segments.get(i).startOffset())
                    .isGreaterThan(segments.get(i - 1).startOffset());
        }
    }

    @Test
    void constructor_rejectsInvalidParams() {
        assertThatIllegalArgumentException().isThrownBy(() -> strategy(0, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> strategy(4, -1));
        assertThatIllegalArgumentException().isThrownBy(() -> strategy(4, 4));
    }

    @Test
    void name_returnsExpectedIdentifier() {
        assertThat(strategy(10, 2).name()).isEqualTo("sliding-window");
    }
}
