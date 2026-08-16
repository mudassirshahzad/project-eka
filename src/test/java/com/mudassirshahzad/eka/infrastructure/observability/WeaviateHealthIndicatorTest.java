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

class WeaviateHealthIndicatorTest {

    private static final String READY_URL = "http://weaviate.internal:8080/v1/.well-known/ready";

    @Test
    void health_ready_returnsUp() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(READY_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        WeaviateHealthIndicator indicator = new WeaviateHealthIndicator(restTemplate, READY_URL);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        server.verify();
    }

    @Test
    void health_notReady_returnsDown() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(READY_URL)).andRespond(withServerError());

        WeaviateHealthIndicator indicator = new WeaviateHealthIndicator(restTemplate, READY_URL);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("reason");
    }

    @Test
    void health_unreachableHost_returnsDownWithoutThrowing() {
        RestTemplate restTemplate = new RestTemplate();
        WeaviateHealthIndicator indicator =
                new WeaviateHealthIndicator(restTemplate, "http://localhost:1/v1/.well-known/ready");

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }
}
