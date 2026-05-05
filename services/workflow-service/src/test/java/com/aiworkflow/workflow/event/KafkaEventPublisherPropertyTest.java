package com.aiworkflow.workflow.event;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for KafkaEventPublisher using jqwik.
 *
 * Mocks are created manually (no @ExtendWith(MockitoExtension.class)) because
 * jqwik manages its own lifecycle and does not support JUnit 5 extensions directly.
 */
class KafkaEventPublisherPropertyTest {

    @SuppressWarnings("unchecked")
    private KafkaTemplate<String, Object> kafkaTemplate;
    private KafkaEventPublisher publisher;

    @BeforeProperty
    void setUp() {
        kafkaTemplate = Mockito.mock(KafkaTemplate.class);
        publisher = new KafkaEventPublisher(kafkaTemplate);
    }

    // -----------------------------------------------------------------------
    // Property 2: Producer failure does not propagate to caller
    // Validates: Requirements 2.6, 7.1
    // -----------------------------------------------------------------------

    /**
     * **Validates: Requirements 2.6, 7.1**
     *
     * For any UUID workflowId and any String workflowName, when KafkaTemplate.send
     * throws a RuntimeException, publishWorkflowCreated should return normally.
     */
    @Property(tries = 10)
    @Tag("Feature: kafka-event-driven")
    void property2_publishWorkflowCreated_doesNotPropagateKafkaFailure(
            @ForAll("uuids") UUID workflowId,
            @ForAll String workflowName) {

        when(kafkaTemplate.send(any(String.class), any(String.class), any()))
                .thenThrow(new RuntimeException("Kafka broker unavailable"));

        assertThatCode(() -> publisher.publishWorkflowCreated(workflowId, workflowName))
                .doesNotThrowAnyException();
    }

    /**
     * **Validates: Requirements 2.6, 7.1**
     *
     * For any UUID workflowId/stepId and any String stepName, when KafkaTemplate.send
     * throws a RuntimeException, publishStepCreated should return normally.
     */
    @Property(tries = 10)
    @Tag("Feature: kafka-event-driven")
    void property2_publishStepCreated_doesNotPropagateKafkaFailure(
            @ForAll("uuids") UUID workflowId,
            @ForAll("uuids") UUID stepId,
            @ForAll String stepName) {

        when(kafkaTemplate.send(any(String.class), any(String.class), any()))
                .thenThrow(new RuntimeException("Kafka broker unavailable"));

        assertThatCode(() -> publisher.publishStepCreated(workflowId, stepId, stepName))
                .doesNotThrowAnyException();
    }

    /**
     * **Validates: Requirements 2.6, 7.1**
     *
     * For any UUID workflowId/stepId, when KafkaTemplate.send throws a RuntimeException,
     * publishStepCompleted should return normally.
     */
    @Property(tries = 10)
    @Tag("Feature: kafka-event-driven")
    void property2_publishStepCompleted_doesNotPropagateKafkaFailure(
            @ForAll("uuids") UUID workflowId,
            @ForAll("uuids") UUID stepId) {

        when(kafkaTemplate.send(any(String.class), any(String.class), any()))
                .thenThrow(new RuntimeException("Kafka broker unavailable"));

        assertThatCode(() -> publisher.publishStepCompleted(workflowId, stepId))
                .doesNotThrowAnyException();
    }

    /**
     * **Validates: Requirements 2.6, 7.1**
     *
     * When KafkaTemplate.send returns a future that completes exceptionally,
     * publishWorkflowCreated should return normally (async failure is swallowed).
     */
    @Property(tries = 10)
    @Tag("Feature: kafka-event-driven")
    void property2_publishWorkflowCreated_asyncFailure_doesNotPropagate(
            @ForAll("uuids") UUID workflowId,
            @ForAll String workflowName) {

        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Async send failed"));

        when(kafkaTemplate.send(any(String.class), any(String.class), any()))
                .thenReturn(failedFuture);

        assertThatCode(() -> publisher.publishWorkflowCreated(workflowId, workflowName))
                .doesNotThrowAnyException();
    }

    // -----------------------------------------------------------------------
    // Property 7: Message key equals workflowId
    // Validates: Requirements 7.7, 8.1
    // -----------------------------------------------------------------------

    /**
     * **Validates: Requirements 7.7, 8.1**
     *
     * For any event published via publishWorkflowCreated, the Kafka message key
     * should equal workflowId.toString().
     */
    @Property(tries = 10)
    @Tag("Feature: kafka-event-driven")
    void property7_publishWorkflowCreated_keyEqualsWorkflowId(
            @ForAll("uuids") UUID workflowId,
            @ForAll String workflowName) {

        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.complete(null);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        when(kafkaTemplate.send(eq(KafkaEventPublisher.TOPIC_WORKFLOW_CREATED), keyCaptor.capture(), any()))
                .thenReturn(future);

        publisher.publishWorkflowCreated(workflowId, workflowName);

        assertThat(keyCaptor.getValue()).isEqualTo(workflowId.toString());
    }

    /**
     * **Validates: Requirements 7.7, 8.1**
     *
     * For any event published via publishStepCreated, the Kafka message key
     * should equal workflowId.toString().
     */
    @Property(tries = 10)
    @Tag("Feature: kafka-event-driven")
    void property7_publishStepCreated_keyEqualsWorkflowId(
            @ForAll("uuids") UUID workflowId,
            @ForAll("uuids") UUID stepId,
            @ForAll String stepName) {

        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.complete(null);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        when(kafkaTemplate.send(eq(KafkaEventPublisher.TOPIC_STEP_CREATED), keyCaptor.capture(), any()))
                .thenReturn(future);

        publisher.publishStepCreated(workflowId, stepId, stepName);

        assertThat(keyCaptor.getValue()).isEqualTo(workflowId.toString());
    }

    /**
     * **Validates: Requirements 7.7, 8.1**
     *
     * For any event published via publishStepCompleted, the Kafka message key
     * should equal workflowId.toString().
     */
    @Property(tries = 10)
    @Tag("Feature: kafka-event-driven")
    void property7_publishStepCompleted_keyEqualsWorkflowId(
            @ForAll("uuids") UUID workflowId,
            @ForAll("uuids") UUID stepId) {

        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.complete(null);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        when(kafkaTemplate.send(eq(KafkaEventPublisher.TOPIC_STEP_COMPLETED), keyCaptor.capture(), any()))
                .thenReturn(future);

        publisher.publishStepCompleted(workflowId, stepId);

        assertThat(keyCaptor.getValue()).isEqualTo(workflowId.toString());
    }

    // -----------------------------------------------------------------------
    // Providers
    // -----------------------------------------------------------------------

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }
}
