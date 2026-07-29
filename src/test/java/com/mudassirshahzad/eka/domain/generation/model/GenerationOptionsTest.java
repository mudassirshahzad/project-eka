package com.mudassirshahzad.eka.domain.generation.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class GenerationOptionsTest {

    // ── DEFAULT constant ──────────────────────────────────────────────────────

    @Test
    void default_hasExpectedValues() {
        assertThat(GenerationOptions.DEFAULT.maxTokens()).isEqualTo(2048);
        assertThat(GenerationOptions.DEFAULT.temperature()).isEqualTo(0.1);
        assertThat(GenerationOptions.DEFAULT.topP()).isEqualTo(1.0);
        assertThat(GenerationOptions.DEFAULT.modelNameOverride()).isNull();
    }

    @Test
    void default_hasNoModelOverride() {
        assertThat(GenerationOptions.DEFAULT.hasModelOverride()).isFalse();
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    void constructor_rejectsMaxTokensLessThanOne() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GenerationOptions(0, 0.5, 1.0, null))
                .withMessageContaining("maxTokens must be >= 1");
    }

    @Test
    void constructor_rejectsTemperatureBelowZero() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GenerationOptions(1024, -0.1, 1.0, null))
                .withMessageContaining("temperature must be in [0.0, 2.0]");
    }

    @Test
    void constructor_rejectsTemperatureAboveTwo() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GenerationOptions(1024, 2.1, 1.0, null))
                .withMessageContaining("temperature must be in [0.0, 2.0]");
    }

    @Test
    void constructor_rejectsTopPBelowZero() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GenerationOptions(1024, 0.5, -0.1, null))
                .withMessageContaining("topP must be in [0.0, 1.0]");
    }

    @Test
    void constructor_rejectsTopPAboveOne() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GenerationOptions(1024, 0.5, 1.1, null))
                .withMessageContaining("topP must be in [0.0, 1.0]");
    }

    // ── hasModelOverride ──────────────────────────────────────────────────────

    @Test
    void hasModelOverride_returnsTrueWhenModelNameOverrideIsSet() {
        var opts = new GenerationOptions(1024, 0.7, 0.9, "llama3.2");
        assertThat(opts.hasModelOverride()).isTrue();
    }

    @Test
    void hasModelOverride_returnsFalseWhenModelNameOverrideIsNull() {
        var opts = new GenerationOptions(1024, 0.7, 0.9, null);
        assertThat(opts.hasModelOverride()).isFalse();
    }

    // ── Boundary values ───────────────────────────────────────────────────────

    @Test
    void constructor_acceptsTemperatureAtZero() {
        var opts = new GenerationOptions(1024, 0.0, 1.0, null);
        assertThat(opts.temperature()).isEqualTo(0.0);
    }

    @Test
    void constructor_acceptsTemperatureAtTwo() {
        var opts = new GenerationOptions(1024, 2.0, 1.0, null);
        assertThat(opts.temperature()).isEqualTo(2.0);
    }

    @Test
    void constructor_acceptsTopPAtZero() {
        var opts = new GenerationOptions(1024, 0.5, 0.0, null);
        assertThat(opts.topP()).isEqualTo(0.0);
    }

    @Test
    void constructor_acceptsMaxTokensOfOne() {
        var opts = new GenerationOptions(1, 0.5, 1.0, null);
        assertThat(opts.maxTokens()).isEqualTo(1);
    }
}
