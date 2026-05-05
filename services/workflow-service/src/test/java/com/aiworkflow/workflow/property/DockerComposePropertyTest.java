package com.aiworkflow.workflow.property;

import net.jqwik.api.*;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for Docker Compose configuration correctness.
 *
 * Tests Properties 2, 3, and 4 from the design document, validating that
 * docker-compose.yml correctly configures build contexts, dependency wiring,
 * and runtime configuration for all application services.
 *
 * Validates: Requirements 2.1, 2.4, 2.5, 2.6, 2.8
 */
class DockerComposePropertyTest {

    /**
     * Application services that must be built from source (not infrastructure).
     */
    private static final List<String> APPLICATION_SERVICES = List.of(
            "eureka-server",
            "api-gateway",
            "workflow-service",
            "user-service",
            "ai-agent-service",
            "notification-service"
    );

    /**
     * Expected port mapping: service name -> host port.
     */
    private static final Map<String, Integer> SERVICE_PORT_MAPPING = Map.of(
            "workflow-service", 8080,
            "user-service", 8081,
            "ai-agent-service", 8082,
            "notification-service", 8084,
            "eureka-server", 8761,
            "api-gateway", 8090
    );

    /**
     * Expected infrastructure dependencies for each application service.
     * eureka-server has no depends_on (it IS infrastructure for other app services).
     */
    private static final Map<String, List<String>> SERVICE_DEPENDENCIES = Map.of(
            "eureka-server", List.of(),
            "api-gateway", List.of("eureka-server"),
            "workflow-service", List.of("postgres-workflow", "kafka", "eureka-server"),
            "user-service", List.of("postgres-user", "eureka-server"),
            "ai-agent-service", List.of("kafka", "eureka-server"),
            "notification-service", List.of("kafka", "eureka-server")
    );

    /**
     * Environment variable keys that should reference container hostnames (not localhost).
     * Maps service name to the env var keys that contain hostnames.
     */
    private static final Map<String, List<String>> SERVICE_HOSTNAME_ENV_VARS = Map.of(
            "eureka-server", List.of(),
            "api-gateway", List.of("EUREKA_CLIENT_SERVICEURL_DEFAULTZONE"),
            "workflow-service", List.of("SPRING_DATASOURCE_URL", "KAFKA_BOOTSTRAP_SERVERS", "EUREKA_CLIENT_SERVICEURL_DEFAULTZONE"),
            "user-service", List.of("SPRING_DATASOURCE_URL", "EUREKA_CLIENT_SERVICEURL_DEFAULTZONE"),
            "ai-agent-service", List.of("KAFKA_BOOTSTRAP_SERVERS", "EUREKA_CLIENT_SERVICEURL_DEFAULTZONE"),
            "notification-service", List.of("KAFKA_BOOTSTRAP_SERVERS", "EUREKA_CLIENT_SERVICEURL_DEFAULTZONE")
    );

    private Map<String, Object> composeConfig;

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadComposeConfig() {
        if (composeConfig != null) {
            return composeConfig;
        }
        Path composePath = resolveComposePath();
        Yaml yaml = new Yaml();
        try (InputStream is = Files.newInputStream(composePath)) {
            composeConfig = yaml.load(is);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read docker-compose.yml at: " + composePath, e);
        }
        return composeConfig;
    }

    /**
     * Resolves the path to docker-compose.yml.
     * Handles running from either the project root or the workflow-service directory.
     */
    private Path resolveComposePath() {
        Path cwd = Paths.get("").toAbsolutePath();

        // When running from workflow-service directory (mvn test)
        Path fromService = cwd.resolve("../../infra/docker/docker-compose.yml").normalize();
        if (Files.exists(fromService)) {
            return fromService;
        }

        // When running from project root
        Path fromRoot = cwd.resolve("infra/docker/docker-compose.yml");
        if (Files.exists(fromRoot)) {
            return fromRoot;
        }

        throw new IllegalStateException(
                "Cannot locate docker-compose.yml from working directory: " + cwd);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getServices() {
        Map<String, Object> config = loadComposeConfig();
        return (Map<String, Object>) config.get("services");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getServiceConfig(String serviceName) {
        Map<String, Object> services = getServices();
        assertThat(services).as("docker-compose.yml must contain a 'services' section")
                .isNotNull();
        Map<String, Object> serviceConfig = (Map<String, Object>) services.get(serviceName);
        assertThat(serviceConfig)
                .as("Service '%s' must be defined in docker-compose.yml", serviceName)
                .isNotNull();
        return serviceConfig;
    }

    @Provide
    Arbitrary<String> applicationServiceNames() {
        return Arbitraries.of(APPLICATION_SERVICES);
    }

    /**
     * Property 2: Docker Compose uses build context for all services
     *
     * For any application service defined in docker-compose.yml, the service definition
     * SHALL contain a `build` key with a valid context path and SHALL NOT contain an
     * `image` key.
     *
     * Validates: Requirements 2.1
     */
    @Property(tries = 10)
    @Tag("Feature: production-readiness")
    @Tag("Property 2: Docker Compose uses build context for all services")
    @SuppressWarnings("unchecked")
    void property2_dockerComposeUsesBuildContextForAllServices(
            @ForAll("applicationServiceNames") String serviceName) {

        Map<String, Object> serviceConfig = getServiceConfig(serviceName);

        // Service SHALL contain a `build` key
        assertThat(serviceConfig).as(
                "Service '%s' must have a 'build' key in docker-compose.yml", serviceName)
                .containsKey("build");

        // Verify build has a valid context path
        Object buildValue = serviceConfig.get("build");
        String contextPath;
        if (buildValue instanceof Map) {
            Map<String, Object> buildMap = (Map<String, Object>) buildValue;
            assertThat(buildMap).as(
                    "Service '%s' build config must have a 'context' key", serviceName)
                    .containsKey("context");
            contextPath = buildMap.get("context").toString();
        } else {
            // build can be a simple string (the context path)
            contextPath = buildValue.toString();
        }

        assertThat(contextPath).as(
                "Service '%s' build context path must not be empty", serviceName)
                .isNotBlank();

        // Service SHALL NOT contain an `image` key
        assertThat(serviceConfig).as(
                "Service '%s' must NOT have an 'image' key when using build context", serviceName)
                .doesNotContainKey("image");
    }

    /**
     * Property 3: Docker Compose dependency wiring
     *
     * For any application service in docker-compose.yml, the service SHALL have
     * `depends_on` entries with `condition: service_healthy` for its infrastructure
     * dependencies, AND environment variables SHALL reference container hostnames
     * (not `localhost`).
     *
     * Validates: Requirements 2.4, 2.5
     */
    @Property(tries = 10)
    @Tag("Feature: production-readiness")
    @Tag("Property 3: Docker Compose dependency wiring")
    @SuppressWarnings("unchecked")
    void property3_dockerComposeDependencyWiring(
            @ForAll("applicationServiceNames") String serviceName) {

        Map<String, Object> serviceConfig = getServiceConfig(serviceName);
        List<String> expectedDeps = SERVICE_DEPENDENCIES.get(serviceName);

        // Check depends_on with health conditions
        if (!expectedDeps.isEmpty()) {
            assertThat(serviceConfig).as(
                    "Service '%s' must have 'depends_on' section", serviceName)
                    .containsKey("depends_on");

            Object dependsOnValue = serviceConfig.get("depends_on");
            assertThat(dependsOnValue).as(
                    "Service '%s' depends_on must be a map (long syntax with conditions)", serviceName)
                    .isInstanceOf(Map.class);

            Map<String, Object> dependsOn = (Map<String, Object>) dependsOnValue;

            for (String expectedDep : expectedDeps) {
                assertThat(dependsOn).as(
                        "Service '%s' must depend on '%s'", serviceName, expectedDep)
                        .containsKey(expectedDep);

                Object depConfig = dependsOn.get(expectedDep);
                assertThat(depConfig).as(
                        "Dependency '%s' for service '%s' must use long syntax with condition",
                        expectedDep, serviceName)
                        .isInstanceOf(Map.class);

                Map<String, Object> depMap = (Map<String, Object>) depConfig;
                assertThat(depMap).as(
                        "Dependency '%s' for service '%s' must have 'condition' key",
                        expectedDep, serviceName)
                        .containsKey("condition");
                assertThat(depMap.get("condition").toString()).as(
                        "Dependency '%s' for service '%s' must have condition 'service_healthy'",
                        expectedDep, serviceName)
                        .isEqualTo("service_healthy");
            }
        }

        // Check environment variables reference container hostnames (not localhost)
        List<String> hostnameEnvVars = SERVICE_HOSTNAME_ENV_VARS.get(serviceName);
        if (!hostnameEnvVars.isEmpty()) {
            assertThat(serviceConfig).as(
                    "Service '%s' must have 'environment' section", serviceName)
                    .containsKey("environment");

            Object envValue = serviceConfig.get("environment");
            Map<String, String> envMap = toStringMap(envValue);

            for (String envKey : hostnameEnvVars) {
                assertThat(envMap).as(
                        "Service '%s' must have environment variable '%s'", serviceName, envKey)
                        .containsKey(envKey);

                String envVal = envMap.get(envKey);
                assertThat(envVal).as(
                        "Service '%s' env var '%s' must not reference localhost (value: %s)",
                        serviceName, envKey, envVal)
                        .doesNotContain("localhost");
            }
        }
    }

    /**
     * Property 4: Docker Compose service runtime configuration
     *
     * For any service in docker-compose.yml, the service SHALL expose the correct
     * host port matching its configured server port AND SHALL have
     * `restart: unless-stopped` configured.
     *
     * Validates: Requirements 2.6, 2.8
     */
    @Property(tries = 10)
    @Tag("Feature: production-readiness")
    @Tag("Property 4: Docker Compose service runtime configuration")
    @SuppressWarnings("unchecked")
    void property4_dockerComposeServiceRuntimeConfiguration(
            @ForAll("applicationServiceNames") String serviceName) {

        Map<String, Object> serviceConfig = getServiceConfig(serviceName);
        int expectedPort = SERVICE_PORT_MAPPING.get(serviceName);

        // Service SHALL expose the correct host port
        assertThat(serviceConfig).as(
                "Service '%s' must have 'ports' section", serviceName)
                .containsKey("ports");

        List<String> ports = ((List<?>) serviceConfig.get("ports")).stream()
                .map(Object::toString)
                .toList();

        String expectedPortMapping = expectedPort + ":" + expectedPort;
        assertThat(ports).as(
                "Service '%s' must expose port mapping '%s'", serviceName, expectedPortMapping)
                .anyMatch(p -> p.equals(expectedPortMapping));

        // Service SHALL have restart: unless-stopped
        assertThat(serviceConfig).as(
                "Service '%s' must have 'restart' key", serviceName)
                .containsKey("restart");
        assertThat(serviceConfig.get("restart").toString()).as(
                "Service '%s' must have restart policy 'unless-stopped'", serviceName)
                .isEqualTo("unless-stopped");
    }

    /**
     * Converts environment value (which can be a Map or a List of "KEY=VALUE" strings)
     * to a Map<String, String>.
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> toStringMap(Object envValue) {
        if (envValue instanceof Map) {
            Map<String, String> result = new LinkedHashMap<>();
            ((Map<String, Object>) envValue).forEach((k, v) ->
                    result.put(k, v != null ? v.toString() : ""));
            return result;
        } else if (envValue instanceof List) {
            Map<String, String> result = new LinkedHashMap<>();
            for (Object item : (List<?>) envValue) {
                String s = item.toString();
                int eqIdx = s.indexOf('=');
                if (eqIdx > 0) {
                    result.put(s.substring(0, eqIdx), s.substring(eqIdx + 1));
                }
            }
            return result;
        }
        return Collections.emptyMap();
    }
}
