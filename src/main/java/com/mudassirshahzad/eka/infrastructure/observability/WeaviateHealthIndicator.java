package com.mudassirshahzad.eka.infrastructure.observability;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Reports whether Weaviate — the vector store backing every retrieval — is reachable and ready
 * (P05.4). Uses the same {@code /v1/.well-known/ready} endpoint {@code docker-compose.yml}'s own
 * container healthcheck already relies on, so "healthy" means the same thing in both places.
 * Contributes to {@code /actuator/health} under the {@code weaviate} key and the {@code readiness}
 * group, not {@code liveness} — the app process is fine even if Weaviate is briefly down.
 *
 * <p>Short, fixed timeout (2s). {@code @Profile("!test")}: no live Weaviate runs in tests, and
 * excluding the bean entirely is more reliable than a {@code management.health.*.enabled} toggle
 * for a hand-written indicator.
 */
@Component
@Profile("!test")
public class WeaviateHealthIndicator implements HealthIndicator {

    private final RestTemplate restTemplate;
    private final String       readyUrl;

    @Autowired
    public WeaviateHealthIndicator(RestTemplateBuilder restTemplateBuilder,
                                    @Value("${spring.ai.vectorstore.weaviate.scheme}") String scheme,
                                    @Value("${spring.ai.vectorstore.weaviate.host}") String host) {
        this(restTemplateBuilder
                        .connectTimeout(Duration.ofSeconds(2))
                        .readTimeout(Duration.ofSeconds(2))
                        .build(),
                scheme + "://" + host + "/v1/.well-known/ready");
    }

    /** Package-private: lets tests bind a {@code MockRestServiceServer} to a known instance. */
    WeaviateHealthIndicator(RestTemplate restTemplate, String readyUrl) {
        this.restTemplate = restTemplate;
        this.readyUrl = readyUrl;
    }

    @Override
    public Health health() {
        try {
            restTemplate.getForEntity(readyUrl, String.class);
            return Health.up().build();
        } catch (RestClientException ex) {
            return Health.down().withDetail("reason", ex.getClass().getSimpleName()).build();
        }
    }
}
