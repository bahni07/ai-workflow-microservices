package com.aiworkflow.notification.consumer;

import com.aiworkflow.notification.event.AiSuggestionGeneratedEvent;
import com.aiworkflow.notification.event.StepCompletedEvent;
import com.aiworkflow.notification.event.StepCreatedEvent;
import com.aiworkflow.notification.event.WorkflowCreatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 1 (notification-service): All event round-trips.
 * Validates: Requirements 5.3, 5.4, 5.5, 5.6, 5.7, 8.2
 */
@Tag("Feature: kafka-event-driven, Property 1: Event serialization round-trip")
class NotificationEventSerializationPropertyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Property(tries = 100)
    void property1a_workflowCreatedEventRoundTrip(
            @ForAll("uuids") UUID workflowId,
            @ForAll @NotBlank @StringLength(max = 255) String workflowName) throws Exception {

        WorkflowCreatedEvent original = new WorkflowCreatedEvent(workflowId, workflowName);
        String json = objectMapper.writeValueAsString(original);
        WorkflowCreatedEvent deserialized = objectMapper.readValue(json, WorkflowCreatedEvent.class);
        assertThat(deserialized).isEqualTo(original);
    }

    @Property(tries = 100)
    void property1b_stepCreatedEventRoundTrip(
            @ForAll("uuids") UUID workflowId,
            @ForAll("uuids") UUID stepId,
            @ForAll @NotBlank @StringLength(max = 255) String stepName) throws Exception {

        StepCreatedEvent original = new StepCreatedEvent(workflowId, stepId, stepName);
        String json = objectMapper.writeValueAsString(original);
        StepCreatedEvent deserialized = objectMapper.readValue(json, StepCreatedEvent.class);
        assertThat(deserialized).isEqualTo(original);
    }

    @Property(tries = 100)
    void property1c_stepCompletedEventRoundTrip(
            @ForAll("uuids") UUID workflowId,
            @ForAll("uuids") UUID stepId) throws Exception {

        StepCompletedEvent original = new StepCompletedEvent(workflowId, stepId);
        String json = objectMapper.writeValueAsString(original);
        StepCompletedEvent deserialized = objectMapper.readValue(json, StepCompletedEvent.class);
        assertThat(deserialized).isEqualTo(original);
    }

    @Property(tries = 100)
    void property1d_aiSuggestionGeneratedEventRoundTrip(
            @ForAll("uuids") UUID workflowId,
            @ForAll @NotBlank @StringLength(max = 500) String suggestion) throws Exception {

        AiSuggestionGeneratedEvent original = new AiSuggestionGeneratedEvent(workflowId, suggestion);
        String json = objectMapper.writeValueAsString(original);
        AiSuggestionGeneratedEvent deserialized = objectMapper.readValue(json, AiSuggestionGeneratedEvent.class);
        assertThat(deserialized).isEqualTo(original);
    }

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }
}
