package com.aiworkflow.agent.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 1 (ai-agent-service): AiSuggestionGeneratedEvent round-trip serialization.
 * Validates: Requirements 5.7, 8.2
 */
@Tag("Feature: kafka-event-driven, Property 1: Event serialization round-trip")
class AiSuggestionEventSerializationPropertyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * For any generated AiSuggestionGeneratedEvent, serialize to JSON then deserialize
     * back and assert equality.
     *
     * Validates: Requirements 5.7, 8.2
     */
    @Property(tries = 100)
    void property1_aiSuggestionGeneratedEventRoundTrip(
            @ForAll("workflowIds") UUID workflowId,
            @ForAll @NotBlank @StringLength(max = 500) String suggestion) throws Exception {

        AiSuggestionGeneratedEvent original = new AiSuggestionGeneratedEvent(workflowId, suggestion);

        String json = objectMapper.writeValueAsString(original);
        AiSuggestionGeneratedEvent deserialized = objectMapper.readValue(json, AiSuggestionGeneratedEvent.class);

        assertThat(deserialized).isEqualTo(original);
    }

    @Provide
    Arbitrary<UUID> workflowIds() {
        return Arbitraries.create(UUID::randomUUID);
    }
}
