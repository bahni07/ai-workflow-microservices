package com.aiworkflow.gateway.property;

import com.github.tomakehurst.wiremock.WireMockServer;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.lifecycle.BeforeTry;
import net.jqwik.spring.JqwikSpringSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Property-based test: any matching path is routed and path is preserved.
 *
 * Feature: api-gateway, Property 1: Any matching path is routed and path is preserved
 * Validates: Requirements 3.1, 3.2
 *
 * For any request path of the form /api/workflows/<suffix>, the Gateway SHALL forward
 * the request to a workflow-service instance AND the forwarded path SHALL equal the
 * original path — no prefix is stripped or rewritten.
 *
 * Integration approach:
 * - WireMock runs on a fixed port (9999) as the downstream workflow-service stub
 * - The gateway route is overridden via test properties to point directly at WireMock
 *   (bypassing lb:// load balancer resolution entirely)
 * - @JqwikSpringSupport bridges Spring's test context into jqwik's property lifecycle,
 *   enabling @Autowired injection in @Property methods
 */
@JqwikSpringSupport
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "spring.cloud.gateway.routes[0].id=workflow-service",
                "spring.cloud.gateway.routes[0].uri=http://localhost:9999",
                "spring.cloud.gateway.routes[0].predicates[0]=Path=/api/workflows/**"
        }
)
class GatewayRoutingPropertyTest {

    private static final WireMockServer wireMock;

    static {
        wireMock = new WireMockServer(9999);
        wireMock.start();
        Runtime.getRuntime().addShutdownHook(new Thread(wireMock::stop));
    }

    /** Reset WireMock state before each individual try so stubs don't bleed across tries. */
    @BeforeTry
    void resetWireMock() {
        wireMock.resetAll();
    }

    /**
     * Feature: api-gateway, Property 1: Any matching path is routed and path is preserved
     * Validates: Requirements 3.1, 3.2
     *
     * For any URL-safe suffix, the gateway must forward the request to the downstream
     * service at exactly /api/workflows/{suffix} — no path rewriting occurs.
     *
     * @JqwikSpringSupport enables @Autowired parameter injection directly in @Property methods.
     */
    @Property(tries = 100)
    void anyWorkflowPathIsRoutedAndPreserved(
            @ForAll("urlSafeSuffixes") String suffix,
            @Autowired WebTestClient webTestClient) {

        // arrange: stub WireMock to accept any path under /api/workflows/
        wireMock.stubFor(get(urlPathMatching("/api/workflows/.*"))
                .willReturn(aResponse().withStatus(200).withBody("ok")));

        // act: send GET /api/workflows/{suffix} through the gateway
        webTestClient.get()
                .uri("/api/workflows/" + suffix)
                .exchange()
                .expectStatus().isOk();

        // assert: WireMock received the request at exactly /api/workflows/{suffix} — path preserved
        wireMock.verify(getRequestedFor(urlEqualTo("/api/workflows/" + suffix)));
    }

    @Provide
    Arbitrary<String> urlSafeSuffixes() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1)
                .ofMaxLength(20);
    }
}
