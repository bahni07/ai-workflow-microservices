package com.aiworkflow.workflow.event;

import java.util.UUID;

public interface EventPublisher {
    void publishWorkflowCreated(UUID workflowId, String workflowName);
    void publishStepCreated(UUID workflowId, UUID stepId, String stepName);
    void publishStepCompleted(UUID workflowId, UUID stepId);
}
