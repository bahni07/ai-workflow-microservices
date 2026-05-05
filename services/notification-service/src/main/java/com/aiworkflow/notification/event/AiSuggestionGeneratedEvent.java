package com.aiworkflow.notification.event;

import java.util.UUID;

public record AiSuggestionGeneratedEvent(UUID workflowId, String suggestion) {}
