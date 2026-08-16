package com.mudassirshahzad.eka.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * P05.5, ADR HD03. Verifies the customizer bean is well-formed and applies cleanly to a real
 * {@code RestClient.Builder} — the actual socket-level timeout behavior it configures can only be
 * proven against a genuinely slow/hanging server, which is out of scope for a unit test; that
 * propagation path (Spring AI's {@code OllamaApiAutoConfiguration} consuming the same Boot-managed
 * {@code RestClient.Builder} this customizer touches) was verified directly against the Spring AI
 * 1.0.0 autoconfiguration bytecode during implementation, not assumed.
 */
class HttpClientTimeoutConfigTest {

    private final HttpClientTimeoutConfig config = new HttpClientTimeoutConfig();

    @Test
    void ollamaRestClientTimeoutCustomizer_appliesToBuilderWithoutThrowing() {
        RestClientCustomizer customizer = config.ollamaRestClientTimeoutCustomizer(5000L, 60000L);

        RestClient.Builder builder = RestClient.builder();

        assertThatCode(() -> customizer.customize(builder)).doesNotThrowAnyException();
        assertThat(builder.build()).isNotNull();
    }

    @Test
    void ollamaRestClientTimeoutCustomizer_isANewInstancePerCall_configurableViaProperties() {
        RestClientCustomizer shortTimeout = config.ollamaRestClientTimeoutCustomizer(1000L, 2000L);
        RestClientCustomizer longTimeout  = config.ollamaRestClientTimeoutCustomizer(10000L, 120000L);

        assertThat(shortTimeout).isNotSameAs(longTimeout);
    }
}
