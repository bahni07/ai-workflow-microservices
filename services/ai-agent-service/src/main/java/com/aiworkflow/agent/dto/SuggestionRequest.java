package com.aiworkflow.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SuggestionRequest(
        @NotBlank @Size(max = 255) String workflowName,
        @Size(max = 50) List<String> existingSteps
) {}
