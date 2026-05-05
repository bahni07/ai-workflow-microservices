package com.aiworkflow.agent.dto;

import com.aiworkflow.agent.model.SuggestionSource;

public record SuggestionResponse(
        String suggestion,
        SuggestionSource source
) {}
