package com.mudassir.eka.domain.generation.model;

/**
 * Stub definition of a callable tool, reserved for the agent milestone.
 *
 * <p>Today this record exists to make the domain contract forward-compatible: both
 * {@link PromptBuildRequest} and {@link PromptRequest} carry a {@code List<ToolDefinition>}
 * that flows as an empty list through the system until the agent milestone activates tool
 * invocation.  No prompt rendering, schema generation, or execution happens in P04.8.
 *
 * <p>When the agent milestone arrives, this record will gain an execution schema and
 * additional metadata without changing any port signature.
 *
 * @param name        unique tool identifier; must not be blank
 * @param description human-readable summary of the tool's purpose; must not be null
 */
public record ToolDefinition(String name, String description) {

    public ToolDefinition {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("name must not be blank");
        if (description == null)
            throw new IllegalArgumentException("description must not be null");
    }
}
