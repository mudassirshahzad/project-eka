package com.mudassirshahzad.eka.infrastructure.config;

import io.weaviate.client.Config;
import io.weaviate.client.WeaviateAuthClient;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.v1.auth.exception.AuthException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.weaviate.autoconfigure.WeaviateVectorStoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Bounds how long a call to Weaviate can hang — closing the gap ADR HD03 documented as deferred
 * technical debt at P05.5 and Phase 7 scheduled for repair (WP-3, ADR OR01).
 *
 * <h3>Why this replaces the auto-configured bean rather than customizing it</h3>
 * <p>ADR HD03 established, by inspecting the Spring AI 1.0.0 autoconfiguration bytecode rather than
 * its documentation, that {@code WeaviateVectorStoreProperties} exposes no timeout property and the
 * auto-configured {@code WeaviateClient} has no {@code RestClientCustomizer}-equivalent hook — so
 * unlike Ollama (see {@link HttpClientTimeoutConfig}) there is nothing to customize. The only way in
 * is to construct {@link Config} by hand, which is exactly what HD03 predicted a fix would require.
 * {@code WeaviateVectorStoreAutoConfiguration#weaviateClient} is annotated
 * {@code @ConditionalOnMissingBean} (verified in the same jar), so declaring this bean takes
 * precedence and the auto-configured one is never created.
 *
 * <h3>Units are seconds, deliberately, unlike the Ollama properties</h3>
 * <p>{@link Config}'s timeout parameters are seconds — its own two-argument constructor passes
 * {@code 60, 60, 60}, verified in the client bytecode. Expressing these properties in seconds
 * rather than following the {@code app.ollama.*-timeout-ms} convention avoids a millisecond-to-second
 * conversion whose rounding could silently turn a sub-second timeout into {@code 0}. Consistency
 * with the underlying API is worth more here than consistency with a sibling property name.
 */
@Slf4j
@Configuration
public class WeaviateClientConfig {

    @Bean
    public WeaviateClient weaviateClient(
            WeaviateVectorStoreProperties properties,
            @Value("${app.weaviate.connect-timeout-seconds:5}") int connectTimeoutSeconds,
            @Value("${app.weaviate.connection-request-timeout-seconds:5}") int connectionRequestTimeoutSeconds,
            @Value("${app.weaviate.read-timeout-seconds:20}") int readTimeoutSeconds) {

        requirePositive("app.weaviate.connect-timeout-seconds", connectTimeoutSeconds);
        requirePositive("app.weaviate.connection-request-timeout-seconds", connectionRequestTimeoutSeconds);
        requirePositive("app.weaviate.read-timeout-seconds", readTimeoutSeconds);

        Config config = new Config(
                properties.getScheme(),
                properties.getHost(),
                Map.of(),
                connectTimeoutSeconds,
                connectionRequestTimeoutSeconds,
                readTimeoutSeconds);

        log.info("Weaviate client configured with connect={}s connectionRequest={}s read={}s",
                connectTimeoutSeconds, connectionRequestTimeoutSeconds, readTimeoutSeconds);

        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return new WeaviateClient(config);
        }
        try {
            return WeaviateAuthClient.apiKey(config, apiKey);
        } catch (AuthException e) {
            // Mirrors the auto-configuration's own failure mode: an unusable client is a startup
            // failure, not something to degrade into at the first retrieval request.
            throw new IllegalStateException("WeaviateClient could not be created.", e);
        }
    }

    private static void requirePositive(String property, int seconds) {
        if (seconds <= 0) {
            // Fails at context startup rather than on the first hung retrieval, matching the
            // fail-fast precedent JwtProperties set for misconfiguration (v0.6.1, ADR EX04).
            throw new IllegalStateException(
                    property + " must be positive, but was " + seconds
                    + ". A non-positive timeout would restore the unbounded-hang behaviour this bean exists to prevent.");
        }
    }
}
