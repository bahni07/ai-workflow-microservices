package com.aiworkflow.workflow.service.impl;

import com.aiworkflow.workflow.dto.*;
import com.aiworkflow.workflow.entity.Step;
import com.aiworkflow.workflow.entity.Workflow;
import com.aiworkflow.workflow.enums.StepStatus;
import com.aiworkflow.workflow.enums.WorkflowStatus;
import com.aiworkflow.workflow.event.EventPublisher;
import com.aiworkflow.workflow.mapper.WorkflowMapper;
import com.aiworkflow.workflow.notification.NotificationLogger;
import com.aiworkflow.workflow.repository.StepRepository;
import com.aiworkflow.workflow.repository.WorkflowRepository;
import com.aiworkflow.workflow.service.AISuggester;
import com.aiworkflow.workflow.service.WorkflowService;
import com.aiworkflow.workflow.validation.WorkflowValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class WorkflowServiceImpl implements WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowServiceImpl.class);

    private final WorkflowRepository workflowRepository;
    private final StepRepository stepRepository;
    private final WorkflowMapper workflowMapper;
    private final AISuggester aiSuggester;
    private final EventPublisher eventPublisher;
    private final NotificationLogger notificationLogger;
    private final WorkflowValidator workflowValidator;

    public WorkflowServiceImpl(WorkflowRepository workflowRepository,
                               StepRepository stepRepository,
                               WorkflowMapper workflowMapper,
                               AISuggester aiSuggester,
                               EventPublisher eventPublisher,
                               NotificationLogger notificationLogger,
                               WorkflowValidator workflowValidator) {
        this.workflowRepository = workflowRepository;
        this.stepRepository = stepRepository;
        this.workflowMapper = workflowMapper;
        this.aiSuggester = aiSuggester;
        this.eventPublisher = eventPublisher;
        this.notificationLogger = notificationLogger;
        this.workflowValidator = workflowValidator;
    }

    @Override
    @Transactional
    public WorkflowResponse createWorkflow(CreateWorkflowRequest request) {
        Workflow workflow = new Workflow();
        workflow.setName(request.name());
        workflow.setStatus(WorkflowStatus.CREATED);
        Workflow saved = workflowRepository.save(workflow);

        try {
            eventPublisher.publishWorkflowCreated(saved.getId(), saved.getName());
        } catch (Exception e) {
            log.error("Failed to publish workflow.created event workflowId={}", saved.getId(), e);
        }

        notificationLogger.logWorkflowCreated(saved.getId(), saved.getName());
        return workflowMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkflowResponse getWorkflow(UUID id) {
        Workflow workflow = workflowValidator.requireWorkflow(id);
        // Force-load lazy steps collection within the transaction
        workflow.getSteps().size();
        return workflowMapper.toResponse(workflow);
    }

    @Override
    @Transactional
    public AddStepResponse addStep(UUID workflowId, AddStepRequest request) {
        Workflow workflow = workflowValidator.requireWorkflow(workflowId);

        Step step = new Step();
        step.setName(request.name());
        step.setStatus(StepStatus.PENDING);
        step.setWorkflow(workflow);

        if (workflow.getStatus() == WorkflowStatus.CREATED) {
            workflow.setStatus(WorkflowStatus.IN_PROGRESS);
            workflowRepository.save(workflow);
        }

        Step savedStep = stepRepository.save(step);

        List<String> existingStepNames = stepRepository.findByWorkflowId(workflowId)
                .stream()
                .map(Step::getName)
                .toList();

        String suggestion = aiSuggester.suggestNextStep(workflow.getName(), existingStepNames);

        try {
            eventPublisher.publishStepCreated(workflowId, savedStep.getId(), savedStep.getName());
        } catch (Exception e) {
            log.error("Failed to publish step.created event workflowId={} stepId={}", workflowId, savedStep.getId(), e);
        }

        notificationLogger.logStepAdded(savedStep.getId(), savedStep.getName(), workflowId);

        StepResponse stepResponse = workflowMapper.toStepResponse(savedStep);
        return new AddStepResponse(stepResponse, suggestion);
    }

    @Override
    @Transactional
    public WorkflowResponse completeStep(UUID workflowId, UUID stepId) {
        Step step = workflowValidator.requireStepInWorkflow(workflowId, stepId);
        step.setStatus(StepStatus.COMPLETED);
        stepRepository.save(step);

        long incompleteCount = stepRepository.countByWorkflowIdAndStatusNot(workflowId, StepStatus.COMPLETED);
        if (incompleteCount == 0) {
            Workflow workflow = step.getWorkflow();
            workflow.setStatus(WorkflowStatus.COMPLETED);
            workflowRepository.save(workflow);
        }

        try {
            eventPublisher.publishStepCompleted(workflowId, stepId);
        } catch (Exception e) {
            log.error("Failed to publish step.completed event workflowId={} stepId={}", workflowId, stepId, e);
        }

        notificationLogger.logStepCompleted(stepId, workflowId);

        Workflow workflow = workflowValidator.requireWorkflow(workflowId);
        workflow.getSteps().size();
        return workflowMapper.toResponse(workflow);
    }
}
