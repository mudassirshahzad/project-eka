package com.mudassir.eka.domain.generation.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ToolDefinitionTest {

    @Test
    void constructor_throwsOnNullName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ToolDefinition(null, "A useful tool"))
                .withMessageContaining("name must not be blank");
    }

    @Test
    void constructor_throwsOnBlankName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ToolDefinition("   ", "A useful tool"))
                .withMessageContaining("name must not be blank");
    }

    @Test
    void constructor_throwsOnEmptyName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ToolDefinition("", "A useful tool"))
                .withMessageContaining("name must not be blank");
    }

    @Test
    void constructor_throwsOnNullDescription() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ToolDefinition("search", null))
                .withMessageContaining("description must not be null");
    }

    @Test
    void constructor_acceptsValidDefinition() {
        var tool = new ToolDefinition("web_search", "Search the web for information");

        assertThat(tool.name()).isEqualTo("web_search");
        assertThat(tool.description()).isEqualTo("Search the web for information");
    }

    @Test
    void constructor_acceptsEmptyDescription() {
        var tool = new ToolDefinition("noop", "");

        assertThat(tool.name()).isEqualTo("noop");
        assertThat(tool.description()).isEmpty();
    }
}
