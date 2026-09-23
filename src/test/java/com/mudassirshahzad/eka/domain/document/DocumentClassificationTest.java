package com.mudassirshahzad.eka.domain.document;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentClassificationTest {

    @Test
    void level_isOrderedPublicToRestricted() {
        assertThat(DocumentClassification.PUBLIC.level()).isLessThan(DocumentClassification.INTERNAL.level());
        assertThat(DocumentClassification.INTERNAL.level()).isLessThan(DocumentClassification.CONFIDENTIAL.level());
        assertThat(DocumentClassification.CONFIDENTIAL.level()).isLessThan(DocumentClassification.RESTRICTED.level());
    }

    @Test
    void parse_exactName_returnsTier() {
        assertThat(DocumentClassification.parse("CONFIDENTIAL")).contains(DocumentClassification.CONFIDENTIAL);
    }

    @Test
    void parse_isCaseInsensitiveAndTrims() {
        assertThat(DocumentClassification.parse(" public ")).contains(DocumentClassification.PUBLIC);
        assertThat(DocumentClassification.parse("restricted")).contains(DocumentClassification.RESTRICTED);
    }

    @Test
    void parse_null_returnsEmpty() {
        assertThat(DocumentClassification.parse(null)).isEmpty();
    }

    @Test
    void parse_blank_returnsEmpty() {
        assertThat(DocumentClassification.parse("   ")).isEmpty();
    }

    @Test
    void parse_unrecognizedValue_returnsEmpty() {
        assertThat(DocumentClassification.parse("TOP_SECRET")).isEmpty();
    }

    @Test
    void levelOf_knownValue_returnsItsLevel() {
        assertThat(DocumentClassification.levelOf("INTERNAL")).isEqualTo(DocumentClassification.INTERNAL.level());
    }

    @Test
    void levelOf_null_returnsUnknownLevel() {
        assertThat(DocumentClassification.levelOf(null)).isEqualTo(DocumentClassification.UNKNOWN_LEVEL);
    }

    @Test
    void levelOf_unrecognizedValue_returnsUnknownLevel() {
        assertThat(DocumentClassification.levelOf("garbage")).isEqualTo(DocumentClassification.UNKNOWN_LEVEL);
    }

    @Test
    void unknownLevel_isHigherThanEveryRealTier() {
        for (DocumentClassification tier : DocumentClassification.values()) {
            assertThat(DocumentClassification.UNKNOWN_LEVEL).isGreaterThan(tier.level());
        }
    }
}
