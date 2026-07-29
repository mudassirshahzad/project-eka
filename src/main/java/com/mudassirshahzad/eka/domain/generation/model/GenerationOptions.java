package com.mudassirshahzad.eka.domain.generation.model;

public record GenerationOptions(
        int    maxTokens,
        double temperature,
        double topP,
        String modelNameOverride
) {

    public static final GenerationOptions DEFAULT = new GenerationOptions(2048, 0.1, 1.0, null);

    public GenerationOptions {
        if (maxTokens < 1)
            throw new IllegalArgumentException("maxTokens must be >= 1 but was " + maxTokens);
        if (temperature < 0.0 || temperature > 2.0)
            throw new IllegalArgumentException("temperature must be in [0.0, 2.0] but was " + temperature);
        if (topP < 0.0 || topP > 1.0)
            throw new IllegalArgumentException("topP must be in [0.0, 1.0] but was " + topP);
    }

    public boolean hasModelOverride() {
        return modelNameOverride != null;
    }
}
