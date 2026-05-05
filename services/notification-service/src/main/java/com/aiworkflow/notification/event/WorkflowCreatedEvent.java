package com.aiworkflow.notification.event;

import java.util.UUID;

public record WorkflowCreatedEvent(UUID workflowId, String workflowName) {}
