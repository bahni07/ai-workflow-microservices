package com.aiworkflow.workflow.dto;

import com.aiworkflow.workflow.enums.StepStatus;

import java.util.UUID;

public record StepResponse(UUID id, UUID workflowId, String name, StepStatus status) {}
