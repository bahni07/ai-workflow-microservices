package com.aiworkflow.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Integration test verifying that the Gateway returns HTTP 503 when no
 * workflow-service instances are registered in the service registry.
 *
 * Validates: Requirement 3.4
 *
 * Eureka discovery is disabled so the load-balancer has no instances to resolve
 * for lb://workflow-service, causing Spring Cloud Gateway to return 503.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class GatewayRoutingNoInstancesTest {

    @Autowired
    private WebTestClient webTestClient;

    /**
     * Requirement 3.4: WHEN no live Workflow_Service instance is registered in the
     * Service_Registry, THE Gateway SHALL return HTTP 503 to the caller.
     */
    @Test
    void noInstancesRegisteredReturns503() {
        webTestClient.get()
                .uri("/api/workflows/anything")
                .exchange()
                .expectStatus().isEqualTo(503);
    }
}
