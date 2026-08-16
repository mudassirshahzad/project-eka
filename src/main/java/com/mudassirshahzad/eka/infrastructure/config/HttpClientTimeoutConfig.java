package com.mudassirshahzad.eka.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;

import java.time.Duration;

/**
 * Bounds how long a call to Ollama can hang (P05.5, ADR HD03). Without this, the {@code RestClient}
 * Spring AI's {@code OllamaApi} builds internally has no configured timeout at all — a stalled
 * Ollama response would block the calling thread indefinitely rather than failing fast into
 * {@code OllamaLlmAdapter}'s existing retry/backoff (ADR R02) or {@code EmbeddingService}'s
 * (ADR R02-mirrored).
 *
 * <p>Works because {@code OllamaApiAutoConfiguration} takes an {@code ObjectProvider<RestClient.Builder>}
 * — the same Boot-managed builder every {@link RestClientCustomizer} bean customizes — rather than
 * constructing its own {@code RestClient} independently; verified against the actual Spring AI
 * 1.0.0 autoconfiguration bytecode, not assumed. A {@code RestTemplateBuilder}-based component
 * (the P05.4 health indicators) is a different builder type entirely and already configures its
 * own timeouts directly — this bean does not affect those.
 */
@Configuration
public class HttpClientTimeoutConfig {

    @Bean
    public RestClientCustomizer ollamaRestClientTimeoutCustomizer(
            @Value("${app.ollama.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${app.ollama.read-timeout-ms:60000}") long readTimeoutMs) {

        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .withReadTimeout(Duration.ofMillis(readTimeoutMs));
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);

        return builder -> builder.requestFactory(requestFactory);
    }
}
