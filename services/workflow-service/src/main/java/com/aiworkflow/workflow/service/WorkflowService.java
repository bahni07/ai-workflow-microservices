package com.aiworkflow.workflow.service;

import com.aiworkflow.workflow.dto.*;
import java.util.UUID;

public interface WorkflowService {
    WorkflowResponse createWorkflow(CreateWorkflowRequest request);
    WorkflowResponse getWorkflow(UUID id);
    AddStepResponse addStep(UUID workflowId, AddStepRequest request);
    WorkflowResponse completeStep(UUID workflowId, UUID stepId);
}
