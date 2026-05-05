package com.aiworkflow.workflow.validation;

import com.aiworkflow.workflow.entity.Step;
import com.aiworkflow.workflow.entity.Workflow;
import com.aiworkflow.workflow.exception.StepNotFoundException;
import com.aiworkflow.workflow.exception.WorkflowNotFoundException;
import com.aiworkflow.workflow.repository.StepRepository;
import com.aiworkflow.workflow.repository.WorkflowRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class WorkflowValidator {

    private final WorkflowRepository workflowRepository;
    private final StepRepository stepRepository;

    public WorkflowValidator(WorkflowRepository workflowRepository, StepRepository stepRepository) {
        this.workflowRepository = workflowRepository;
        this.stepRepository = stepRepository;
    }

    public Workflow requireWorkflow(UUID workflowId) {
        return workflowRepository.findById(workflowId)
                .orElseThrow(() -> new WorkflowNotFoundException(workflowId));
    }

    public Step requireStepInWorkflow(UUID workflowId, UUID stepId) {
        Step step = stepRepository.findById(stepId)
                .orElseThrow(() -> new StepNotFoundException(stepId));
        if (!step.getWorkflow().getId().equals(workflowId)) {
            throw new StepNotFoundException(stepId);
        }
        return step;
    }
}
