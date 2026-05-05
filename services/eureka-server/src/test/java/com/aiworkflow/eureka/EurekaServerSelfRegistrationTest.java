package com.aiworkflow.eureka;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying the Eureka Server does not register itself as a client.
 * Validates: Requirement 1.3
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class EurekaServerSelfRegistrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void eurekaServerDoesNotRegisterItself() {
        // If registerWithEureka: false is correctly set, querying for EUREKA-SERVER
        // in the registry should return 404 — it is not registered as a client.
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:8761/eureka/apps/EUREKA-SERVER", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
