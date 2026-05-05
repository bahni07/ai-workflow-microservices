package com.aiworkflow.notification.consumer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.aiworkflow.notification.event.AiSuggestionGeneratedEvent;
import com.aiworkflow.notification.event.StepCompletedEvent;
import com.aiworkflow.notification.event.StepCreatedEvent;
import com.aiworkflow.notification.event.WorkflowCreatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 5: Notification log format completeness.
 * Validates: Requirements 4.5, 4.6, 4.7, 4.8
 */
@Tag("Feature: kafka-event-driven, Property 5: Notification log format completeness")
class NotificationEventConsumerPropertyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ListAppender<ILoggingEvent> attachListAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(NotificationEventConsumer.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        return appender;
    }

    private void detachAppender(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(NotificationEventConsumer.class);
        logger.detachAppender(appender);
    }

    /**
     * Property 5a: WorkflowCreatedEvent log contains workflowId and workflowName.
     * Validates: Requirements 4.5
     */
    @Property(tries = 100)
    @Tag("Feature: kafka-event-driven, Property 5: Notification log format completeness")
    void property5a_workflowCreatedLogContainsRequiredFields(
            @ForAll("uuids") UUID workflowId,
            @ForAll @NotBlank @StringLength(max = 255) String workflowName) throws Exception {

        ListAppender<ILoggingEvent> appender = attachListAppender();
        try {
            NotificationEventConsumer consumer = new NotificationEventConsumer(objectMapper);
            WorkflowCreatedEvent event = new WorkflowCreatedEvent(workflowId, workflowName);
            String json = objectMapper.writeValueAsString(event);
            ConsumerRecord<String, String> record =
                    new ConsumerRecord<>("workflow.created", 0, 0L, workflowId.toString(), json);

            consumer.onEvent(record);

            String logOutput = appender.list.stream()
                    .filter(e -> e.getLevel() == Level.INFO)
                    .map(ILoggingEvent::getFormattedMessage)
                    .reduce("", String::concat);

            assertThat(logOutput).contains(workflowId.toString());
            assertThat(logOutput).contains(workflowName);
        } finally {
            detachAppender(appender);
        }
    }

    /**
     * Property 5b: StepCreatedEvent log contains workflowId, stepId, and stepName.
     * Validates: Requirements 4.6
     */
    @Property(tries = 100)
    @Tag("Feature: kafka-event-driven, Property 5: Notification log format completeness")
    void property5b_stepCreatedLogContainsRequiredFields(
            @ForAll("uuids") UUID workflowId,
            @ForAll("uuids") UUID stepId,
            @ForAll @NotBlank @StringLength(max = 255) String stepName) throws Exception {

        ListAppender<ILoggingEvent> appender = attachListAppender();
        try {
            NotificationEventConsumer consumer = new NotificationEventConsumer(objectMapper);
            StepCreatedEvent event = new StepCreatedEvent(workflowId, stepId, stepName);
            String json = objectMapper.writeValueAsString(event);
            ConsumerRecord<String, String> record =
                    new ConsumerRecord<>("step.created", 0, 0L, workflowId.toString(), json);

            consumer.onEvent(record);

            String logOutput = appender.list.stream()
                    .filter(e -> e.getLevel() == Level.INFO)
                    .map(ILoggingEvent::getFormattedMessage)
                    .reduce("", String::concat);

            assertThat(logOutput).contains(workflowId.toString());
            assertThat(logOutput).contains(stepId.toString());
            assertThat(logOutput).contains(stepName);
        } finally {
            detachAppender(appender);
        }
    }

    /**
     * Property 5c: StepCompletedEvent log contains workflowId and stepId.
     * Validates: Requirements 4.7
     */
    @Property(tries = 100)
    @Tag("Feature: kafka-event-driven, Property 5: Notification log format completeness")
    void property5c_stepCompletedLogContainsRequiredFields(
            @ForAll("uuids") UUID workflowId,
            @ForAll("uuids") UUID stepId) throws Exception {

        ListAppender<ILoggingEvent> appender = attachListAppender();
        try {
            NotificationEventConsumer consumer = new NotificationEventConsumer(objectMapper);
            StepCompletedEvent event = new StepCompletedEvent(workflowId, stepId);
            String json = objectMapper.writeValueAsString(event);
            ConsumerRecord<String, String> record =
                    new ConsumerRecord<>("step.completed", 0, 0L, workflowId.toString(), json);

            consumer.onEvent(record);

            String logOutput = appender.list.stream()
                    .filter(e -> e.getLevel() == Level.INFO)
                    .map(ILoggingEvent::getFormattedMessage)
                    .reduce("", String::concat);

            assertThat(logOutput).contains(workflowId.toString());
            assertThat(logOutput).contains(stepId.toString());
        } finally {
            detachAppender(appender);
        }
    }

    /**
     * Property 5d: AiSuggestionGeneratedEvent log contains workflowId and suggestion.
     * Validates: Requirements 4.8
     */
    @Property(tries = 100)
    @Tag("Feature: kafka-event-driven, Property 5: Notification log format completeness")
    void property5d_aiSuggestionLogContainsRequiredFields(
            @ForAll("uuids") UUID workflowId,
            @ForAll @NotBlank @StringLength(max = 500) String suggestion) throws Exception {

        ListAppender<ILoggingEvent> appender = attachListAppender();
        try {
            NotificationEventConsumer consumer = new NotificationEventConsumer(objectMapper);
            AiSuggestionGeneratedEvent event = new AiSuggestionGeneratedEvent(workflowId, suggestion);
            String json = objectMapper.writeValueAsString(event);
            ConsumerRecord<String, String> record =
                    new ConsumerRecord<>("ai.suggestion.generated", 0, 0L, workflowId.toString(), json);

            consumer.onEvent(record);

            String logOutput = appender.list.stream()
                    .filter(e -> e.getLevel() == Level.INFO)
                    .map(ILoggingEvent::getFormattedMessage)
                    .reduce("", String::concat);

            assertThat(logOutput).contains(workflowId.toString());
            assertThat(logOutput).contains(suggestion);
        } finally {
            detachAppender(appender);
        }
    }

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }
}
