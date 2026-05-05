package com.aiworkflow.workflow.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        MDC.clear();
    }

    @Test
    void shouldUseProvidedCorrelationId() throws IOException, ServletException {
        String providedId = "test-correlation-id-123";
        request.addHeader("X-Correlation-Id", providedId);

        AtomicReference<String> mdcValue = new AtomicReference<>();
        FilterChain chain = (req, res) -> mdcValue.set(MDC.get("correlationId"));

        filter.doFilter(request, response, chain);

        assertEquals(providedId, mdcValue.get());
        assertEquals(providedId, response.getHeader("X-Correlation-Id"));
    }

    @Test
    void shouldGenerateCorrelationIdWhenHeaderMissing() throws IOException, ServletException {
        AtomicReference<String> mdcValue = new AtomicReference<>();
        FilterChain chain = (req, res) -> mdcValue.set(MDC.get("correlationId"));

        filter.doFilter(request, response, chain);

        assertNotNull(mdcValue.get());
        assertDoesNotThrow(() -> UUID.fromString(mdcValue.get()));
        assertEquals(mdcValue.get(), response.getHeader("X-Correlation-Id"));
    }

    @Test
    void shouldGenerateCorrelationIdWhenHeaderIsBlank() throws IOException, ServletException {
        request.addHeader("X-Correlation-Id", "   ");

        AtomicReference<String> mdcValue = new AtomicReference<>();
        FilterChain chain = (req, res) -> mdcValue.set(MDC.get("correlationId"));

        filter.doFilter(request, response, chain);

        assertNotNull(mdcValue.get());
        assertDoesNotThrow(() -> UUID.fromString(mdcValue.get()));
    }

    @Test
    void shouldClearMdcAfterRequest() throws IOException, ServletException {
        request.addHeader("X-Correlation-Id", "test-id");
        FilterChain chain = (req, res) -> {};

        filter.doFilter(request, response, chain);

        assertNull(MDC.get("correlationId"));
    }

    @Test
    void shouldClearMdcEvenWhenChainThrows() {
        request.addHeader("X-Correlation-Id", "test-id");
        FilterChain chain = (req, res) -> { throw new ServletException("test error"); };

        assertThrows(ServletException.class, () -> filter.doFilter(request, response, chain));
        assertNull(MDC.get("correlationId"));
    }

    @Test
    void shouldSetResponseHeaderWithGeneratedId() throws IOException, ServletException {
        FilterChain chain = (req, res) -> {};

        filter.doFilter(request, response, chain);

        String responseHeader = response.getHeader("X-Correlation-Id");
        assertNotNull(responseHeader);
        assertDoesNotThrow(() -> UUID.fromString(responseHeader));
    }
}
