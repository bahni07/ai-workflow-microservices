package com.aiworkflow.notification.event;

import java.util.UUID;

public record StepCreatedEvent(UUID workflowId, UUID stepId, String stepName) {}
