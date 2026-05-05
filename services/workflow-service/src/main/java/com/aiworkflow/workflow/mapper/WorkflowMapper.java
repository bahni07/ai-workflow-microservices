package com.aiworkflow.workflow.mapper;

import com.aiworkflow.workflow.dto.StepResponse;
import com.aiworkflow.workflow.dto.WorkflowResponse;
import com.aiworkflow.workflow.entity.Step;
import com.aiworkflow.workflow.entity.Workflow;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WorkflowMapper {

    public WorkflowResponse toResponse(Workflow workflow) {
        List<StepResponse> steps = workflow.getSteps().stream()
                .map(this::toStepResponse)
                .toList();
        return new WorkflowResponse(workflow.getId(), workflow.getName(), workflow.getStatus(), steps);
    }

    public StepResponse toStepResponse(Step step) {
        return new StepResponse(step.getId(), step.getWorkflow().getId(), step.getName(), step.getStatus());
    }
}
