package com.aiworkflow.workflow.property;

import net.jqwik.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for README documentation completeness.
 *
 * Tests Properties 8 and 9 from the design document, validating that
 * the README documents all REST API endpoints and all Kafka topics.
 *
 * Validates: Requirements 4.6, 4.7
 */
class ReadmeCompletenessPropertyTest {

    /**
     * Known REST API endpoints across all services: (HTTP method, path).
     */
    private static final List<EndpointEntry> API_ENDPOINTS = List.of(
            new EndpointEntry("POST", "/workflows"),
            new EndpointEntry("GET", "/workflows/{id}"),
            new EndpointEntry("POST", "/workflows/{id}/steps"),
            new EndpointEntry("POST", "/workflows/{workflowId}/steps/{stepId}/complete"),
            new EndpointEntry("POST", "/auth/register"),
            new EndpointEntry("POST", "/auth/login"),
            new EndpointEntry("POST", "/suggestions")
    );

    /**
     * Known Kafka topics used in the platform.
     */
    private static final List<String> KAFKA_TOPICS = List.of(
            "workflow.created",
            "step.created",
            "step.completed",
            "ai.suggestion.generated"
    );

    private String readmeContent;

    private String loadReadmeContent() {
        if (readmeContent != null) {
            return readmeContent;
        }
        Path readmePath = resolveReadmePath();
        try {
            readmeContent = Files.readString(readmePath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read README.md at: " + readmePath, e);
        }
        return readmeContent;
    }

    /**
     * Resolves the path to README.md.
     * Handles running from either the project root or the workflow-service directory.
     */
    private Path resolveReadmePath() {
        Path cwd = Paths.get("").toAbsolutePath();

        // When running from workflow-service directory (mvn test)
        Path fromService = cwd.resolve("../../README.md").normalize();
        if (Files.exists(fromService)) {
            return fromService;
        }

        // When running from project root
        Path fromRoot = cwd.resolve("README.md");
        if (Files.exists(fromRoot)) {
            return fromRoot;
        }

        throw new IllegalStateException(
                "Cannot locate README.md from working directory: " + cwd);
    }

    @Provide
    Arbitrary<EndpointEntry> apiEndpoints() {
        return Arbitraries.of(API_ENDPOINTS);
    }

    @Provide
    Arbitrary<String> kafkaTopics() {
        return Arbitraries.of(KAFKA_TOPICS);
    }

    /**
     * Property 8: README documents all API endpoints
     *
     * For any REST endpoint defined in the codebase (across workflow-service,
     * user-service, and ai-agent-service), the README SHALL contain a corresponding
     * entry with the HTTP method and path.
     *
     * Validates: Requirements 4.6
     */
    @Property(tries = 10)
    @Tag("Feature: production-readiness")
    @Tag("Property 8: README documents all API endpoints")
    void property8_readmeDocumentsAllApiEndpoints(
            @ForAll("apiEndpoints") EndpointEntry endpoint) {

        String readme = loadReadmeContent();

        assertThat(readme).as(
                "README must contain HTTP method '%s' for endpoint '%s'",
                endpoint.method(), endpoint.path())
                .contains(endpoint.method());

        assertThat(readme).as(
                "README must contain path '%s' for endpoint %s %s",
                endpoint.path(), endpoint.method(), endpoint.path())
                .contains(endpoint.path());
    }

    /**
     * Property 9: README documents all Kafka topics
     *
     * For any Kafka topic used in the platform (workflow.created, step.created,
     * step.completed, ai.suggestion.generated), the README SHALL contain a
     * corresponding entry documenting the topic.
     *
     * Validates: Requirements 4.7
     */
    @Property(tries = 10)
    @Tag("Feature: production-readiness")
    @Tag("Property 9: README documents all Kafka topics")
    void property9_readmeDocumentsAllKafkaTopics(
            @ForAll("kafkaTopics") String topic) {

        String readme = loadReadmeContent();

        assertThat(readme).as(
                "README must contain documentation for Kafka topic '%s'", topic)
                .contains(topic);
    }

    /**
     * Record representing an API endpoint with HTTP method and path.
     */
    record EndpointEntry(String method, String path) {
    }
}
