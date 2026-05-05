package com.aiworkflow.agent.kafka;

import java.util.UUID;

public record WorkflowCreatedEvent(UUID workflowId, String workflowName) {}
