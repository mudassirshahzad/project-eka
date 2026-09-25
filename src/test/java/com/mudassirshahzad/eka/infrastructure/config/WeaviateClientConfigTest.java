package com.mudassirshahzad.eka.infrastructure.config;

import io.weaviate.client.WeaviateClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.weaviate.autoconfigure.WeaviateVectorStoreProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the bean that closes ADR HD03's deferred Weaviate-timeout debt (WP-3, ADR OR01).
 *
 * <p>These assertions deliberately stop at construction. Proving that a socket timeout actually
 * fires would require a real Weaviate that hangs on demand, which is an integration concern; what
 * is verifiable here — and what previously did not exist at all — is that the client is built from
 * a {@code Config} carrying bounded timeouts instead of the client library's 60-second defaults.
 */
class WeaviateClientConfigTest {

    private final WeaviateClientConfig config = new WeaviateClientConfig();

    private WeaviateVectorStoreProperties properties(String scheme, String host, String apiKey) {
        WeaviateVectorStoreProperties props = new WeaviateVectorStoreProperties();
        props.setScheme(scheme);
        props.setHost(host);
        props.setApiKey(apiKey);
        return props;
    }

    @Test
    void buildsAClientWithoutAnApiKey() {
        WeaviateClient client = config.weaviateClient(
                properties("http", "localhost:8080", null), 5, 5, 20);

        assertThat(client).isNotNull();
    }

    @Test
    void blankApiKeyIsTreatedAsAbsent_notAsAnEmptyCredential() {
        WeaviateClient client = config.weaviateClient(
                properties("http", "localhost:8080", "   "), 5, 5, 20);

        assertThat(client).isNotNull();
    }

    @Test
    void nonPositiveTimeoutsAreRejectedAtStartup_notSilentlyAccepted() {
        WeaviateVectorStoreProperties props = properties("http", "localhost:8080", null);

        // A zero or negative timeout would restore the unbounded-hang behaviour this bean exists
        // to remove, so it fails the context rather than starting in a known-bad state (ADR EX04).
        assertThatThrownBy(() -> config.weaviateClient(props, 0, 5, 20))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("connect-timeout-seconds");

        assertThatThrownBy(() -> config.weaviateClient(props, 5, -1, 20))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("connection-request-timeout-seconds");

        assertThatThrownBy(() -> config.weaviateClient(props, 5, 5, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("read-timeout-seconds");
    }
}
