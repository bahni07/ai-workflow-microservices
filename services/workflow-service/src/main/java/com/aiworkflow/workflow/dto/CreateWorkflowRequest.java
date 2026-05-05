package com.aiworkflow.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWorkflowRequest(
        @NotBlank @Size(max = 255) String name
) {}
