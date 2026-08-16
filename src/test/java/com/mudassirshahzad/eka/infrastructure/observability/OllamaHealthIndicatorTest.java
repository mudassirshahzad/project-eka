package com.mudassirshahzad.eka.infrastructure.observability;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OllamaHealthIndicatorTest {

    private static final String BASE_URL = "http://ollama.internal:11434";

    @Test
    void health_reachable_returnsUp() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(BASE_URL + "/"))
                .andRespond(withSuccess("Ollama is running", MediaType.TEXT_PLAIN));

        OllamaHealthIndicator indicator = new OllamaHealthIndicator(restTemplate, BASE_URL);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        server.verify();
    }

    @Test
    void health_serverError_returnsDown() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(BASE_URL + "/")).andRespond(withServerError());

        OllamaHealthIndicator indicator = new OllamaHealthIndicator(restTemplate, BASE_URL);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("reason");
    }

    @Test
    void health_unreachableHost_returnsDownWithoutThrowing() {
        RestTemplate restTemplate = new RestTemplate();
        // No server bound at all — connection is refused rather than mocked, exercising the
        // real exception path a genuinely-down Ollama would trigger.
        OllamaHealthIndicator indicator = new OllamaHealthIndicator(restTemplate, "http://localhost:1");

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }
}
