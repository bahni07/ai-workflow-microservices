package com.aiworkflow.agent.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ChatCompletionRequest(
        String model,
        List<ChatMessage> messages,
        double temperature,
        @JsonProperty("max_tokens") int maxTokens
) {}
