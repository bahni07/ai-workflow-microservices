package com.aiworkflow.agent.kafka;

import java.util.UUID;

public record AiSuggestionGeneratedEvent(UUID workflowId, String suggestion) {}
