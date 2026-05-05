package com.aiworkflow.workflow.property;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.logstash.logback.encoder.LogstashEncoder;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;

import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for logging configuration across all services.
 *
 * Property 5: Logging dependency present in all services
 * Property 6: Logback configuration completeness
 * Property 7: Structured JSON log output contains required fields
 *
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7
 */
class LoggingConfigPropertyTest {

    private static final List<String> ALL_SERVICES = List.of(
            "workflow-service",
            "user-service",
            "ai-agent-service",
            "notification-service",
            "eureka-server",
            "api-gateway"
    );

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Resolves the base path to the services directory.
     * Handles running from either the project root or the workflow-service directory.
     */
    private Path resolveServicesBasePath() {
        Path cwd = Paths.get("").toAbsolutePath();
        // When running from workflow-service directory (mvn test), go up to services/
        Path fromService = cwd.getParent();
        if (fromService != null && Files.isDirectory(fromService.resolve("workflow-service"))) {
            return fromService;
        }
        // When running from project root
        Path fromRoot = cwd.resolve("services");
        if (Files.isDirectory(fromRoot.resolve("workflow-service"))) {
            return fromRoot;
        }
        throw new IllegalStateException(
                "Cannot locate services directory from working directory: " + cwd);
    }

    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.of(ALL_SERVICES);
    }

    // -----------------------------------------------------------------------
    // Property 5: Logging dependency present in all services
    // -----------------------------------------------------------------------

    /**
     * Property 5: Logging dependency present in all services
     *
     * For any service in the platform, the service's pom.xml SHALL contain a
     * dependency on logstash-logback-encoder.
     *
     * **Validates: Requirements 3.1**
     */
    @Property(tries = 10)
    @Tag("Feature: production-readiness")
    @Tag("Property 5: Logging dependency present in all services")
    void property5_loggingDependencyPresentInAllServices(
            @ForAll("serviceNames") String serviceName) throws IOException {

        Path servicesBase = resolveServicesBasePath();
        Path pomPath = servicesBase.resolve(serviceName).resolve("pom.xml");

        assertThat(pomPath).as("pom.xml should exist for service: %s", serviceName)
                .exists();

        String pomContent = Files.readString(pomPath);

        assertThat(pomContent)
                .as("pom.xml for %s must contain logstash-logback-encoder dependency", serviceName)
                .contains("logstash-logback-encoder");

        assertThat(pomContent)
                .as("pom.xml for %s must reference net.logstash.logback groupId", serviceName)
                .contains("net.logstash.logback");
    }

    // -----------------------------------------------------------------------
    // Property 6: Logback configuration completeness
    // -----------------------------------------------------------------------

    /**
     * Property 6: Logback configuration completeness
     *
     * For any service in the platform, the service SHALL have a logback-spring.xml
     * file that contains: a default-profile appender using PatternLayout for plain text,
     * a docker-profile appender using LogstashEncoder for JSON, and a root log level
     * configured via ${LOG_LEVEL:-INFO}.
     *
     * **Validates: Requirements 3.2, 3.3, 3.4, 3.7**
     */
    @Property(tries = 10)
    @Tag("Feature: production-readiness")
    @Tag("Property 6: Logback configuration completeness")
    void property6_logbackConfigurationCompleteness(
            @ForAll("serviceNames") String serviceName) throws IOException {

        Path servicesBase = resolveServicesBasePath();
        Path logbackPath = servicesBase.resolve(serviceName)
                .resolve("src/main/resources/logback-spring.xml");

        // Requirement 3.2: logback-spring.xml must exist
        assertThat(logbackPath)
                .as("logback-spring.xml should exist for service: %s", serviceName)
                .exists();

        String content = Files.readString(logbackPath);

        // Requirement 3.3: Default profile uses PatternLayout for plain text
        assertThat(content)
                .as("logback-spring.xml for %s must have a non-docker profile section (default profile)", serviceName)
                .contains("!docker");

        // Verify the pattern element exists with standard log format tokens
        assertThat(content)
                .as("logback-spring.xml for %s must contain a <pattern> element for plain text output", serviceName)
                .contains("<pattern>");

        assertThat(content)
                .as("logback-spring.xml for %s pattern must include date format (%%d)", serviceName)
                .containsPattern("%d\\{");

        assertThat(content)
                .as("logback-spring.xml for %s pattern must include thread (%%thread)", serviceName)
                .contains("%thread");

        assertThat(content)
                .as("logback-spring.xml for %s pattern must include log level", serviceName)
                .contains("level");

        assertThat(content)
                .as("logback-spring.xml for %s pattern must include logger name (%%logger)", serviceName)
                .contains("%logger");

        assertThat(content)
                .as("logback-spring.xml for %s pattern must include message (%%msg)", serviceName)
                .contains("%msg");

        // Requirement 3.4: Docker profile uses LogstashEncoder for JSON
        assertThat(content)
                .as("logback-spring.xml for %s must have a docker profile section", serviceName)
                .contains("<springProfile name=\"docker\">");

        assertThat(content)
                .as("logback-spring.xml for %s must use LogstashEncoder in docker profile", serviceName)
                .contains("LogstashEncoder");

        // Requirement 3.7: Root log level configurable via LOG_LEVEL env var with INFO default
        assertThat(content)
                .as("logback-spring.xml for %s must configure root level via LOG_LEVEL with INFO default", serviceName)
                .contains("${LOG_LEVEL:-INFO}");
    }

    // -----------------------------------------------------------------------
    // Property 7: Structured JSON log output contains required fields
    // -----------------------------------------------------------------------

    /**
     * Provides arbitrary non-empty log messages for property 7.
     */
    @Provide
    Arbitrary<String> logMessages() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(200);
    }

    /**
     * Property 7: Structured JSON log output contains required fields
     *
     * For any log message produced with the docker profile active, the JSON output
     * SHALL contain the fields: @timestamp, level, logger_name, message, service_name,
     * and thread_name. When an exception is logged, the JSON output SHALL additionally
     * contain stack_trace.
     *
     * **Validates: Requirements 3.5, 3.6**
     *
     * NOTE: This is a runtime test. We configure LogstashEncoder programmatically,
     * log messages (with and without exceptions), and verify the JSON output contains
     * all required fields.
     */
    @Property(tries = 10)
    @Tag("Feature: production-readiness")
    @Tag("Property 7: Structured JSON log output contains required fields")
    void property7_structuredJsonLogOutputContainsRequiredFields(
            @ForAll("logMessages") String message) throws IOException {

        // Use the SLF4J-bound LoggerContext to ensure MDC adapter is initialized
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        LogstashEncoder encoder = new LogstashEncoder();
        encoder.setContext(loggerContext);
        encoder.setCustomFields("{\"service_name\":\"test-service\"}");
        encoder.start();

        try {
            // --- Test normal log message (no exception) ---
            LoggingEvent normalEvent = new LoggingEvent();
            normalEvent.setLoggerContext(loggerContext);
            normalEvent.setLoggerName("com.aiworkflow.test.TestLogger");
            normalEvent.setLevel(Level.INFO);
            normalEvent.setMessage(message);
            normalEvent.setThreadName("main");
            normalEvent.setTimeStamp(System.currentTimeMillis());

            byte[] normalBytes = encoder.encode(normalEvent);
            String normalJson = new String(normalBytes, StandardCharsets.UTF_8).trim();

            JsonNode normalNode = OBJECT_MAPPER.readTree(normalJson);

            // Requirement 3.5: JSON must contain required fields
            assertThat(normalNode.has("@timestamp"))
                    .as("JSON log must contain @timestamp field")
                    .isTrue();
            assertThat(normalNode.has("level"))
                    .as("JSON log must contain level field")
                    .isTrue();
            assertThat(normalNode.has("logger_name"))
                    .as("JSON log must contain logger_name field")
                    .isTrue();
            assertThat(normalNode.has("message"))
                    .as("JSON log must contain message field")
                    .isTrue();
            assertThat(normalNode.has("service_name"))
                    .as("JSON log must contain service_name field")
                    .isTrue();
            assertThat(normalNode.has("thread_name"))
                    .as("JSON log must contain thread_name field")
                    .isTrue();

            // Verify field values are correct
            assertThat(normalNode.get("level").asText()).isEqualTo("INFO");
            assertThat(normalNode.get("logger_name").asText()).isEqualTo("com.aiworkflow.test.TestLogger");
            assertThat(normalNode.get("message").asText()).isEqualTo(message);
            assertThat(normalNode.get("service_name").asText()).isEqualTo("test-service");
            assertThat(normalNode.get("thread_name").asText()).isEqualTo("main");

            // --- Test log message with exception ---
            LoggingEvent exceptionEvent = new LoggingEvent();
            exceptionEvent.setLoggerContext(loggerContext);
            exceptionEvent.setLoggerName("com.aiworkflow.test.TestLogger");
            exceptionEvent.setLevel(Level.ERROR);
            exceptionEvent.setMessage(message);
            exceptionEvent.setThreadName("main");
            exceptionEvent.setTimeStamp(System.currentTimeMillis());

            RuntimeException testException = new RuntimeException("Test exception for: " + message);
            exceptionEvent.setThrowableProxy(
                    new ch.qos.logback.classic.spi.ThrowableProxy(testException));

            byte[] exceptionBytes = encoder.encode(exceptionEvent);
            String exceptionJson = new String(exceptionBytes, StandardCharsets.UTF_8).trim();

            JsonNode exceptionNode = OBJECT_MAPPER.readTree(exceptionJson);

            // Requirement 3.6: Exception log must contain stack_trace
            assertThat(exceptionNode.has("stack_trace"))
                    .as("JSON log with exception must contain stack_trace field")
                    .isTrue();
            assertThat(exceptionNode.get("stack_trace").asText())
                    .as("stack_trace must contain the exception message")
                    .contains("Test exception for: " + message);

            // Exception log must also contain all standard fields
            assertThat(exceptionNode.has("@timestamp"))
                    .as("Exception JSON log must contain @timestamp field")
                    .isTrue();
            assertThat(exceptionNode.has("level"))
                    .as("Exception JSON log must contain level field")
                    .isTrue();
            assertThat(exceptionNode.has("logger_name"))
                    .as("Exception JSON log must contain logger_name field")
                    .isTrue();
            assertThat(exceptionNode.has("message"))
                    .as("Exception JSON log must contain message field")
                    .isTrue();
            assertThat(exceptionNode.has("service_name"))
                    .as("Exception JSON log must contain service_name field")
                    .isTrue();
            assertThat(exceptionNode.has("thread_name"))
                    .as("Exception JSON log must contain thread_name field")
                    .isTrue();
        } finally {
            encoder.stop();
        }
    }
}
