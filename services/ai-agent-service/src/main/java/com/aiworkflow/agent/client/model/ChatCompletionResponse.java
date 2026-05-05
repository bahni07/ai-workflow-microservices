package com.aiworkflow.agent.client.model;

import java.util.List;

public record ChatCompletionResponse(List<Choice> choices) {}
