package com.aiworkflow.notification.consumer;

import com.aiworkflow.notification.event.AiSuggestionGeneratedEvent;
import com.aiworkflow.notification.event.StepCompletedEvent;
import com.aiworkflow.notification.event.StepCreatedEvent;
import com.aiworkflow.notification.event.WorkflowCreatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);

    private final ObjectMapper objectMapper;

    public NotificationEventConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {"workflow.created", "step.created", "step.completed", "ai.suggestion.generated"},
            groupId = "notification-service")
    public void onEvent(ConsumerRecord<String, String> record) {
        try {
            switch (record.topic()) {
                case "workflow.created" -> {
                    WorkflowCreatedEvent event = objectMapper.readValue(record.value(), WorkflowCreatedEvent.class);
                    log.info("event=workflow.created workflowId={} workflowName={}",
                            event.workflowId(), event.workflowName());
                }
                case "step.created" -> {
                    StepCreatedEvent event = objectMapper.readValue(record.value(), StepCreatedEvent.class);
                    log.info("event=step.created workflowId={} stepId={} stepName={}",
                            event.workflowId(), event.stepId(), event.stepName());
                }
                case "step.completed" -> {
                    StepCompletedEvent event = objectMapper.readValue(record.value(), StepCompletedEvent.class);
                    log.info("event=step.completed workflowId={} stepId={}",
                            event.workflowId(), event.stepId());
                }
                case "ai.suggestion.generated" -> {
                    AiSuggestionGeneratedEvent event =
                            objectMapper.readValue(record.value(), AiSuggestionGeneratedEvent.class);
                    log.info("event=ai.suggestion.generated workflowId={} suggestion={}",
                            event.workflowId(), event.suggestion());
                }
                default -> log.warn("event=unknown topic={} key={}", record.topic(), record.key());
            }
        } catch (Exception e) {
            log.error("event=deserialization.failed topic={} key={} value={}",
                    record.topic(), record.key(), record.value(), e);
        }
    }
}
