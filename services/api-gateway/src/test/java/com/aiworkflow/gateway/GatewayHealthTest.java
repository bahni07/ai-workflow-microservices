package com.aiworkflow.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Integration test for the Gateway health endpoint.
 *
 * Requirements: 2.3, 2.9
 *
 * Eureka is disabled to allow the gateway to start without a running registry.
 * The health endpoint must return HTTP 200 and be accessible without authentication.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class GatewayHealthTest {

    @Autowired
    private WebTestClient webTestClient;

    /**
     * Requirement 2.3: Gateway SHALL expose a Health_Endpoint at /actuator/health
     * returning HTTP 200 when running.
     */
    @Test
    void healthEndpointReturnsHttp200() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    /**
     * Requirement 2.9: Gateway SHALL expose the Health_Endpoint without requiring authentication.
     * No Authorization header is sent — the endpoint must still return 200.
     */
    @Test
    void healthEndpointIsAccessibleWithoutAuthentication() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }
}
