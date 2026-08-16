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
 * Reports whether the Ollama server backing generation, query rewriting, and embedding is
 * reachable — none of those work without it, so this is genuine, actionable operational signal
 * (P05.4), not a decorative check. Contributes to {@code /actuator/health} under the {@code ollama}
 * key (bean-name convention) and the {@code readiness} health group (see {@code application.yml}) —
 * deliberately not {@code liveness}: an unreachable Ollama means the app can't serve generation
 * requests, not that the app process itself is broken, so it shouldn't trigger a container restart.
 *
 * <p>A short, fixed timeout (2s) keeps a slow/hanging Ollama from making {@code /actuator/health}
 * itself slow to answer. {@code @Profile("!test")}: no live Ollama runs in tests, and excluding the
 * bean entirely is more reliable than a {@code management.health.*.enabled} toggle for a
 * hand-written indicator.
 */
@Component
@Profile("!test")
public class OllamaHealthIndicator implements HealthIndicator {

    private final RestTemplate restTemplate;
    private final String       baseUrl;

    @Autowired
    public OllamaHealthIndicator(RestTemplateBuilder restTemplateBuilder,
                                  @Value("${spring.ai.ollama.base-url}") String baseUrl) {
        this(restTemplateBuilder
                        .connectTimeout(Duration.ofSeconds(2))
                        .readTimeout(Duration.ofSeconds(2))
                        .build(),
                baseUrl);
    }

    /** Package-private: lets tests bind a {@code MockRestServiceServer} to a known instance. */
    OllamaHealthIndicator(RestTemplate restTemplate, String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    @Override
    public Health health() {
        try {
            restTemplate.getForEntity(baseUrl + "/", String.class);
            return Health.up().build();
        } catch (RestClientException ex) {
            return Health.down().withDetail("reason", ex.getClass().getSimpleName()).build();
        }
    }
}
