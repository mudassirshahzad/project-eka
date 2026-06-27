package com.mudassir.eka.domain.retrieval;

import com.mudassir.eka.domain.retrieval.model.RetrievalOptions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class RetrievalOptionsTest {

    @Test
    void defaultOptions_haveCorrectValues() {
        assertThat(RetrievalOptions.DEFAULT.topK()).isEqualTo(10);
        assertThat(RetrievalOptions.DEFAULT.minimumScore()).isEqualTo(0.0);
    }

    @Test
    void of_withTopKOnly_usesDefaultMinimumScore() {
        RetrievalOptions opts = RetrievalOptions.of(5);

        assertThat(opts.topK()).isEqualTo(5);
        assertThat(opts.minimumScore()).isEqualTo(RetrievalOptions.DEFAULT_MINIMUM_SCORE);
    }

    @Test
    void of_withTopKAndMinimumScore_setsExplicitValues() {
        RetrievalOptions opts = RetrievalOptions.of(20, 0.75);

        assertThat(opts.topK()).isEqualTo(20);
        assertThat(opts.minimumScore()).isEqualTo(0.75);
    }

    @Test
    void topK_atUpperBound_isValid() {
        assertThatNoException().isThrownBy(() -> RetrievalOptions.of(RetrievalOptions.MAX_TOP_K));
    }

    @Test
    void minimumScore_atUpperBound_isValid() {
        assertThatNoException().isThrownBy(() -> RetrievalOptions.of(10, 1.0));
    }

    @Test
    void topK_zero_throwsIllegalArgumentException() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RetrievalOptions.of(0))
                .withMessageContaining("topK");
    }

    @Test
    void topK_exceedsMaximum_throwsIllegalArgumentException() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RetrievalOptions.of(RetrievalOptions.MAX_TOP_K + 1))
                .withMessageContaining("topK");
    }

    @Test
    void minimumScore_negative_throwsIllegalArgumentException() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RetrievalOptions.of(10, -0.001))
                .withMessageContaining("minimumScore");
    }

    @Test
    void minimumScore_exceedsOne_throwsIllegalArgumentException() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RetrievalOptions.of(10, 1.001))
                .withMessageContaining("minimumScore");
    }
}
