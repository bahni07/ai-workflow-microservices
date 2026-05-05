package com.aiworkflow.workflow.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class NotificationLogger {

    private static final Logger log = LoggerFactory.getLogger(NotificationLogger.class);

    public void logWorkflowCreated(UUID workflowId, String workflowName) {
        log.info("workflowId={} workflowName={}", workflowId, workflowName);
    }

    public void logStepAdded(UUID stepId, String stepName, UUID workflowId) {
        log.info("stepId={} stepName={} workflowId={}", stepId, stepName, workflowId);
    }

    public void logStepCompleted(UUID stepId, UUID workflowId) {
        log.info("stepId={} workflowId={}", stepId, workflowId);
    }

    public void logError(String message, Throwable cause) {
        log.error(message, cause);
    }
}
