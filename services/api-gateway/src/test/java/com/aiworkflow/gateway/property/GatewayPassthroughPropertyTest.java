package com.aiworkflow.gateway.property;

import com.github.tomakehurst.wiremock.WireMockServer;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.BeforeTry;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Property-based test: response passthrough is transparent.
 *
 * Feature: api-gateway, Property 2: Response passthrough is transparent
 * Validates: Requirements 3.3, 3.5
 *
 * For any HTTP response returned by the Workflow_Service (any status code 200–599,
 * any body content), the Gateway SHALL return that exact status code and body to the
 * original caller without modification.
 *
 * Uses a separate WireMock port (9998) to remain independent of GatewayRoutingPropertyTest (9999).
 */
@JqwikSpringSupport
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "spring.cloud.gateway.routes[0].id=workflow-service",
                "spring.cloud.gateway.routes[0].uri=http://localhost:9998",
                "spring.cloud.gateway.routes[0].predicates[0]=Path=/api/workflows/**"
        }
)
class GatewayPassthroughPropertyTest {

    private static final WireMockServer wireMock;

    static {
        wireMock = new WireMockServer(9998);
        wireMock.start();
        Runtime.getRuntime().addShutdownHook(new Thread(wireMock::stop));
    }

    /** Reset WireMock state before each individual try so stubs don't bleed across tries. */
    @BeforeTry
    void resetWireMock() {
        wireMock.resetAll();
    }

    /**
     * Feature: api-gateway, Property 2: Response passthrough is transparent
     * Validates: Requirements 3.3, 3.5
     *
     * For any status code in 200–599 (excluding HTTP no-body codes 204, 205, 304
     * where the protocol forbids a response body) and any simple ASCII body, the
     * gateway must return the exact same status code and body to the caller — no modification.
     */
    @Property(tries = 100)
    void gatewayPassesThroughAnyStatusCodeAndBody(
            @ForAll("statusCodesWithBody") int statusCode,
            @ForAll("urlSafeBodies") String body,
            @Autowired WebTestClient webTestClient) {

        // arrange: stub WireMock to return the generated status code and body
        wireMock.stubFor(get(urlPathMatching("/api/workflows/.*"))
                .willReturn(aResponse()
                        .withStatus(statusCode)
                        .withHeader("Content-Type", "text/plain")
                        .withBody(body)));

        // act + assert: gateway must pass through status code and body unchanged
        webTestClient.get()
                .uri("/api/workflows/test")
                .exchange()
                .expectStatus().isEqualTo(statusCode)
                .expectBody(String.class)
                .isEqualTo(body);
    }

    @Provide
    Arbitrary<Integer> statusCodesWithBody() {
        // Exclude HTTP no-body status codes: 204 No Content, 205 Reset Content, 304 Not Modified.
        // The HTTP protocol forbids a message body on these responses, so the network stack
        // strips any body WireMock sends — this is correct behaviour, not a gateway bug.
        return Arbitraries.integers()
                .between(200, 599)
                .filter(code -> code != 204 && code != 205 && code != 304);
    }

    @Provide
    Arbitrary<String> urlSafeBodies() {
        // ofMinLength(1) avoids empty bodies: WebTestClient returns null for empty response bodies,
        // which would cause a spurious assertion failure unrelated to gateway passthrough behaviour.
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1)
                .ofMaxLength(50);
    }
}
