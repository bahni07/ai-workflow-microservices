package com.aiworkflow.workflow.dto;

import com.aiworkflow.workflow.enums.WorkflowStatus;

import java.util.List;
import java.util.UUID;

public record WorkflowResponse(UUID id, String name, WorkflowStatus status, List<StepResponse> steps) {}
