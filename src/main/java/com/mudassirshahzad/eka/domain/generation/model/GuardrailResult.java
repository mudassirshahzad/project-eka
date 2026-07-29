package com.mudassirshahzad.eka.domain.generation.model;

import java.util.Objects;

public record GuardrailResult(
        GuardrailStatus status,
        String          text
) {

    public GuardrailResult {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(text,   "text must not be null");
    }

    public boolean isPassed() {
        return status == GuardrailStatus.PASS;
    }

    public boolean isBlocked() {
        return status == GuardrailStatus.BLOCK;
    }

    public static GuardrailResult pass(String text) {
        return new GuardrailResult(GuardrailStatus.PASS, text);
    }

    public static GuardrailResult block(String safeText) {
        return new GuardrailResult(GuardrailStatus.BLOCK, safeText);
    }
}
