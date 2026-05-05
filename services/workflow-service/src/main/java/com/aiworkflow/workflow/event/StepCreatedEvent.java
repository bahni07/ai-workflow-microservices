package com.aiworkflow.workflow.event;

import java.util.UUID;

public record StepCreatedEvent(UUID workflowId, UUID stepId, String stepName) {}
