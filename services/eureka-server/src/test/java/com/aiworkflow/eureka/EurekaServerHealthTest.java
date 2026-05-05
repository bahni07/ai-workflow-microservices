package com.aiworkflow.eureka;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying the Eureka Server health endpoint.
 * Requirements: 1.6, 1.7
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class EurekaServerHealthTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void healthEndpointReturnsHttp200() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:8761/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void healthEndpointReturnsStatusUp() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:8761/actuator/health", String.class);

        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void healthEndpointRequiresNoAuthentication() {
        // TestRestTemplate sends no Authorization header by default — this verifies
        // the endpoint is accessible without any credentials (Requirement 1.7)
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:8761/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
