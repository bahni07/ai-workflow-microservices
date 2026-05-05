package com.aiworkflow.workflow.event;

import java.util.UUID;

public record StepCompletedEvent(UUID workflowId, UUID stepId) {}
