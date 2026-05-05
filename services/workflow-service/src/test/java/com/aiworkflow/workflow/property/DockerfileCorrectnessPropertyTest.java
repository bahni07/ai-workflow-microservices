package com.aiworkflow.workflow.property;

import net.jqwik.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for Dockerfile correctness across all services.
 *
 * Property 1: Dockerfile security and port correctness
 *
 * For any service in the platform, the service's Dockerfile SHALL specify a non-root
 * USER directive AND the EXPOSE port SHALL match the service's configured server.port
 * from application.yml.
 *
 * Validates: Requirements 1.4, 1.5
 */
class DockerfileCorrectnessPropertyTest {

    private static final Map<String, Integer> SERVICE_PORT_MAPPING = Map.of(
            "workflow-service", 8080,
            "user-service", 8081,
            "ai-agent-service", 8082,
            "notification-service", 8084,
            "eureka-server", 8761,
            "api-gateway", 8090
    );

    private static final Pattern USER_PATTERN = Pattern.compile("^USER\\s+(\\S+)", Pattern.MULTILINE);
    private static final Pattern EXPOSE_PATTERN = Pattern.compile("^EXPOSE\\s+(\\d+)", Pattern.MULTILINE);

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
        return Arbitraries.of(SERVICE_PORT_MAPPING.keySet().toArray(new String[0]));
    }

    /**
     * Property 1: Dockerfile security and port correctness
     *
     * For any service in the platform, the service's Dockerfile SHALL specify a non-root
     * USER directive AND the EXPOSE port SHALL match the service's configured server.port.
     *
     * Validates: Requirements 1.4, 1.5
     */
    @Property(tries = 10)
    @Tag("Feature: production-readiness")
    @Tag("Property 1: Dockerfile security and port correctness")
    void property1_dockerfileSecurityAndPortCorrectness(
            @ForAll("serviceNames") String serviceName) throws IOException {

        Path servicesBase = resolveServicesBasePath();
        Path dockerfile = servicesBase.resolve(serviceName).resolve("Dockerfile");

        assertThat(dockerfile).exists()
                .as("Dockerfile should exist for service: %s", serviceName);

        String content = Files.readString(dockerfile);

        // Requirement 1.4: Service SHALL run as a non-root user
        Matcher userMatcher = USER_PATTERN.matcher(content);
        assertThat(userMatcher.find())
                .as("Dockerfile for %s must contain a USER directive", serviceName)
                .isTrue();

        String user = userMatcher.group(1);
        assertThat(user)
                .as("Dockerfile for %s must specify a non-root user, but found: %s", serviceName, user)
                .isNotEqualTo("root");

        // Requirement 1.5: EXPOSE port SHALL match the service's configured server.port
        int expectedPort = SERVICE_PORT_MAPPING.get(serviceName);

        Matcher exposeMatcher = EXPOSE_PATTERN.matcher(content);
        assertThat(exposeMatcher.find())
                .as("Dockerfile for %s must contain an EXPOSE directive", serviceName)
                .isTrue();

        int exposedPort = Integer.parseInt(exposeMatcher.group(1));
        assertThat(exposedPort)
                .as("Dockerfile for %s must EXPOSE port %d, but found %d", serviceName, expectedPort, exposedPort)
                .isEqualTo(expectedPort);
    }
}
