package com.aiworkflow.workflow.event;

import java.util.UUID;

public record WorkflowCreatedEvent(UUID workflowId, String workflowName) {}
