package com.aiworkflow.agent.service;

import com.aiworkflow.agent.dto.SuggestionRequest;
import com.aiworkflow.agent.dto.SuggestionResponse;

public interface AiSuggestionService {
    SuggestionResponse suggest(SuggestionRequest request);
}
