package com.aiworkflow.workflow.event;

import com.aiworkflow.workflow.constants.EventNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "false")
public class StubEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(StubEventPublisher.class);

    @Override
    public void publishWorkflowCreated(UUID workflowId, String workflowName) {
        try {
            log.info("event={} workflowId={} workflowName={}", EventNames.WORKFLOW_CREATED, workflowId, workflowName);
        } catch (Exception e) {
            log.error("Failed to publish event={}", EventNames.WORKFLOW_CREATED, e);
        }
    }

    @Override
    public void publishStepCreated(UUID workflowId, UUID stepId, String stepName) {
        try {
            log.info("event={} workflowId={} stepId={} stepName={}", EventNames.STEP_CREATED, workflowId, stepId, stepName);
        } catch (Exception e) {
            log.error("Failed to publish event={}", EventNames.STEP_CREATED, e);
        }
    }

    @Override
    public void publishStepCompleted(UUID workflowId, UUID stepId) {
        try {
            log.info("event={} workflowId={} stepId={}", EventNames.STEP_COMPLETED, workflowId, stepId);
        } catch (Exception e) {
            log.error("Failed to publish event={}", EventNames.STEP_COMPLETED, e);
        }
    }
}
