package com.aiworkflow.workflow.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    static final String TOPIC_WORKFLOW_CREATED = "workflow.created";
    static final String TOPIC_STEP_CREATED = "step.created";
    static final String TOPIC_STEP_COMPLETED = "step.completed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publishWorkflowCreated(UUID workflowId, String workflowName) {
        WorkflowCreatedEvent event = new WorkflowCreatedEvent(workflowId, workflowName);
        try {
            kafkaTemplate.send(TOPIC_WORKFLOW_CREATED, workflowId.toString(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("event.publish.failed topic={} eventType={} workflowId={}",
                                    TOPIC_WORKFLOW_CREATED, WorkflowCreatedEvent.class.getSimpleName(), workflowId, ex);
                        }
                    });
        } catch (Exception e) {
            log.error("event.publish.failed topic={} eventType={} workflowId={}",
                    TOPIC_WORKFLOW_CREATED, WorkflowCreatedEvent.class.getSimpleName(), workflowId, e);
        }
    }

    @Override
    public void publishStepCreated(UUID workflowId, UUID stepId, String stepName) {
        StepCreatedEvent event = new StepCreatedEvent(workflowId, stepId, stepName);
        try {
            kafkaTemplate.send(TOPIC_STEP_CREATED, workflowId.toString(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("event.publish.failed topic={} eventType={} workflowId={}",
                                    TOPIC_STEP_CREATED, StepCreatedEvent.class.getSimpleName(), workflowId, ex);
                        }
                    });
        } catch (Exception e) {
            log.error("event.publish.failed topic={} eventType={} workflowId={}",
                    TOPIC_STEP_CREATED, StepCreatedEvent.class.getSimpleName(), workflowId, e);
        }
    }

    @Override
    public void publishStepCompleted(UUID workflowId, UUID stepId) {
        StepCompletedEvent event = new StepCompletedEvent(workflowId, stepId);
        try {
            kafkaTemplate.send(TOPIC_STEP_COMPLETED, workflowId.toString(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("event.publish.failed topic={} eventType={} workflowId={}",
                                    TOPIC_STEP_COMPLETED, StepCompletedEvent.class.getSimpleName(), workflowId, ex);
                        }
                    });
        } catch (Exception e) {
            log.error("event.publish.failed topic={} eventType={} workflowId={}",
                    TOPIC_STEP_COMPLETED, StepCompletedEvent.class.getSimpleName(), workflowId, e);
        }
    }
}
