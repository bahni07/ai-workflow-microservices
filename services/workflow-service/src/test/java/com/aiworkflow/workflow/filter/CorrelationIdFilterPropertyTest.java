package com.aiworkflow.workflow.filter;

import jakarta.servlet.ServletException;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for CorrelationIdFilter.
 *
 * **Validates: Requirements 3.5**
 */
class CorrelationIdFilterPropertyTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Property(tries = 10)
    @Tag("production-readiness")
    @Tag("correlationId-propagation")
    void providedCorrelationIdShouldBePropagatedToMdcAndResponse(@ForAll @NotBlank String correlationId)
            throws IOException, ServletException {
        // **Validates: Requirements 3.5**
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("X-Correlation-Id", correlationId);

        AtomicReference<String> mdcValue = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> mdcValue.set(MDC.get("correlationId")));

        assertEquals(correlationId, mdcValue.get(),
                "MDC correlationId should match the provided header value");
        assertEquals(correlationId, response.getHeader("X-Correlation-Id"),
                "Response header should echo the provided correlation ID");
        assertNull(MDC.get("correlationId"),
                "MDC should be cleared after the filter completes");
    }

    @Property(tries = 10)
    @Tag("production-readiness")
    @Tag("correlationId-generation")
    void missingHeaderShouldGenerateValidUuidCorrelationId(@ForAll("httpMethods") String method)
            throws IOException, ServletException {
        // **Validates: Requirements 3.5**
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setMethod(method);

        AtomicReference<String> mdcValue = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> mdcValue.set(MDC.get("correlationId")));

        assertNotNull(mdcValue.get(), "MDC correlationId should be set");
        assertDoesNotThrow(() -> UUID.fromString(mdcValue.get()),
                "Generated correlationId should be a valid UUID");
        assertEquals(mdcValue.get(), response.getHeader("X-Correlation-Id"),
                "Response header should contain the generated correlation ID");
        assertNull(MDC.get("correlationId"),
                "MDC should be cleared after the filter completes");
    }

    @Provide
    Arbitrary<String> httpMethods() {
        return Arbitraries.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");
    }
}
