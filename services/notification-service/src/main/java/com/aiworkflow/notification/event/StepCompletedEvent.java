package com.aiworkflow.notification.event;

import java.util.UUID;

public record StepCompletedEvent(UUID workflowId, UUID stepId) {}
