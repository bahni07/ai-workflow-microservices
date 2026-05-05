package com.aiworkflow.workflow.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for event POJO serialization using jqwik.
 *
 * Property 1: Event serialization round-trip
 * Property 6: Null fields serialize as JSON null
 *
 * Validates: Requirements 2.5, 5.4, 5.5, 5.6, 5.8, 8.2
 */
class EventSerializationPropertyTest {

    private ObjectMapper objectMapper;

    @BeforeProperty
    void setUp() {
        objectMapper = new ObjectMapper();
        // Register ParameterNamesModule so Jackson can deserialize Java records
        // (records use their canonical constructor parameter names as JSON property names)
        objectMapper.registerModule(new ParameterNamesModule());
        // Ensure null fields are included in serialization (not omitted)
        objectMapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        // Disable writing dates as timestamps for consistency
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // -----------------------------------------------------------------------
    // Property 1: Event serialization round-trip
    // Validates: Requirements 2.5, 5.4, 5.5, 5.6, 8.2
    // -----------------------------------------------------------------------

    /**
     * **Validates: Requirements 2.5, 5.4, 8.2**
     *
     * For any WorkflowCreatedEvent, serializing to JSON and deserializing back
     * should produce an object equal to the original.
     */
    @Property(tries = 10)
    @Tag("Feature: kafka-event-driven")
    void property1_workflowCreatedEvent_roundTrip(
            @ForAll("uuids") UUID workflowId,
            @ForAll String workflowName) throws Exception {

        WorkflowCreatedEvent original = new WorkflowCreatedEvent(workflowId, workflowName);
        String json = objectMapper.writeValueAsString(original);
        WorkflowCreatedEvent deserialized = objectMapper.readValue(json, WorkflowCreatedEvent.class);

        assertThat(deserialized).isEqualTo(original);
    }

    /**
     * **Validates: Requirements 2.5, 5.5, 8.2**
     *
     * For any StepCreatedEvent, serializing to JSON and deserializing back
     * should produce an object equal to the original.
     */
    @Property(tries = 10)
    @Tag("Feature: kafka-event-driven")
    void property1_stepCreatedEvent_roundTrip(
            @ForAll("uuids") UUID workflowId,
            @ForAll("uuids") UUID stepId,
            @ForAll String stepName) throws Exception {

        StepCreatedEvent original = new StepCreatedEvent(workflowId, stepId, stepName);
        String json = objectMapper.writeValueAsString(original);
        StepCreatedEvent deserialized = objectMapper.readValue(json, StepCreatedEvent.class);

        assertThat(deserialized).isEqualTo(original);
    }

    /**
     * **Validates: Requirements 2.5, 5.6, 8.2**
     *
     * For any StepCompletedEvent, serializing to JSON and deserializing back
     * should produce an object equal to the original.
     */
    @Property(tries = 10)
    @Tag("Feature: kafka-event-driven")
    void property1_stepCompletedEvent_roundTrip(
            @ForAll("uuids") UUID workflowId,
            @ForAll("uuids") UUID stepId) throws Exception {

        StepCompletedEvent original = new StepCompletedEvent(workflowId, stepId);
        String json = objectMapper.writeValueAsString(original);
        StepCompletedEvent deserialized = objectMapper.readValue(json, StepCompletedEvent.class);

        assertThat(deserialized).isEqualTo(original);
    }

    // -----------------------------------------------------------------------
    // Property 6: Null fields serialize as JSON null
    // Validates: Requirements 5.8
    // -----------------------------------------------------------------------

    /**
     * **Validates: Requirements 5.8**
     *
     * For a WorkflowCreatedEvent with a null workflowName, the serialized JSON
     * should contain "workflowName":null (key present, value null).
     */
    @Property(tries = 10)
    @Tag("Feature: kafka-event-driven")
    void property6_workflowCreatedEvent_nullWorkflowName_serializesAsJsonNull(
            @ForAll("uuids") UUID workflowId) throws Exception {

        WorkflowCreatedEvent event = new WorkflowCreatedEvent(workflowId, null);
        String json = objectMapper.writeValueAsString(event);

        assertThat(json).contains("\"workflowName\":null");
    }

    /**
     * **Validates: Requirements 5.8**
     *
     * For a StepCreatedEvent with a null stepName, the serialized JSON
     * should contain "stepName":null (key present, value null).
     */
    @Property(tries = 10)
    @Tag("Feature: kafka-event-driven")
    void property6_stepCreatedEvent_nullStepName_serializesAsJsonNull(
            @ForAll("uuids") UUID workflowId,
            @ForAll("uuids") UUID stepId) throws Exception {

        StepCreatedEvent event = new StepCreatedEvent(workflowId, stepId, null);
        String json = objectMapper.writeValueAsString(event);

        assertThat(json).contains("\"stepName\":null");
    }

    // -----------------------------------------------------------------------
    // Providers
    // -----------------------------------------------------------------------

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }
}
