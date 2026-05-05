package com.aiworkflow.workflow.service;

import com.aiworkflow.workflow.dto.*;
import com.aiworkflow.workflow.entity.Step;
import com.aiworkflow.workflow.entity.Workflow;
import com.aiworkflow.workflow.enums.StepStatus;
import com.aiworkflow.workflow.enums.WorkflowStatus;
import com.aiworkflow.workflow.event.EventPublisher;
import com.aiworkflow.workflow.exception.WorkflowNotFoundException;
import com.aiworkflow.workflow.mapper.WorkflowMapper;
import com.aiworkflow.workflow.notification.NotificationLogger;
import com.aiworkflow.workflow.repository.StepRepository;
import com.aiworkflow.workflow.repository.WorkflowRepository;
import com.aiworkflow.workflow.service.impl.WorkflowServiceImpl;
import com.aiworkflow.workflow.validation.WorkflowValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceImplTest {

    @Mock private WorkflowRepository workflowRepository;
    @Mock private StepRepository stepRepository;
    @Mock private WorkflowMapper workflowMapper;
    @Mock private AISuggester aiSuggester;
    @Mock private EventPublisher eventPublisher;
    @Mock private NotificationLogger notificationLogger;
    @Mock private WorkflowValidator workflowValidator;

    @InjectMocks
    private WorkflowServiceImpl service;

    private UUID workflowId;
    private Workflow workflow;

    @BeforeEach
    void setUp() {
        workflowId = UUID.randomUUID();
        workflow = new Workflow();
        workflow.setId(workflowId);
        workflow.setName("Test Workflow");
        workflow.setStatus(WorkflowStatus.CREATED);
        workflow.setSteps(new ArrayList<>());
    }

    // --- createWorkflow ---

    @Test
    void createWorkflow_happyPath_savesAndPublishesAndLogs() {
        CreateWorkflowRequest request = new CreateWorkflowRequest("Test Workflow");
        WorkflowResponse expectedResponse = new WorkflowResponse(workflowId, "Test Workflow", WorkflowStatus.CREATED, List.of());

        when(workflowRepository.save(any(Workflow.class))).thenReturn(workflow);
        when(workflowMapper.toResponse(workflow)).thenReturn(expectedResponse);

        WorkflowResponse result = service.createWorkflow(request);

        assertThat(result).isEqualTo(expectedResponse);
        verify(workflowRepository).save(any(Workflow.class));
        verify(eventPublisher).publishWorkflowCreated(workflowId, "Test Workflow");
        verify(notificationLogger).logWorkflowCreated(workflowId, "Test Workflow");
        verify(workflowMapper).toResponse(workflow);
    }

    @Test
    void createWorkflow_eventPublisherThrows_stillReturnsResponse() {
        CreateWorkflowRequest request = new CreateWorkflowRequest("Test Workflow");
        WorkflowResponse expectedResponse = new WorkflowResponse(workflowId, "Test Workflow", WorkflowStatus.CREATED, List.of());

        when(workflowRepository.save(any(Workflow.class))).thenReturn(workflow);
        doThrow(new RuntimeException("Kafka down")).when(eventPublisher).publishWorkflowCreated(any(), any());
        when(workflowMapper.toResponse(workflow)).thenReturn(expectedResponse);

        WorkflowResponse result = service.createWorkflow(request);

        assertThat(result).isEqualTo(expectedResponse);
        verify(notificationLogger).logWorkflowCreated(workflowId, "Test Workflow");
    }

    // --- getWorkflow ---

    @Test
    void getWorkflow_happyPath_callsValidatorAndMapper() {
        WorkflowResponse expectedResponse = new WorkflowResponse(workflowId, "Test Workflow", WorkflowStatus.CREATED, List.of());

        when(workflowValidator.requireWorkflow(workflowId)).thenReturn(workflow);
        when(workflowMapper.toResponse(workflow)).thenReturn(expectedResponse);

        WorkflowResponse result = service.getWorkflow(workflowId);

        assertThat(result).isEqualTo(expectedResponse);
        verify(workflowValidator).requireWorkflow(workflowId);
        verify(workflowMapper).toResponse(workflow);
    }

    @Test
    void getWorkflow_notFound_throwsWorkflowNotFoundException() {
        when(workflowValidator.requireWorkflow(workflowId))
                .thenThrow(new WorkflowNotFoundException(workflowId));

        assertThatThrownBy(() -> service.getWorkflow(workflowId))
                .isInstanceOf(WorkflowNotFoundException.class);
    }

    // --- addStep ---

    @Test
    void addStep_happyPath_savesStepTransitionsStatusAndReturnsWithSuggestion() {
        AddStepRequest request = new AddStepRequest("Step One");
        UUID stepId = UUID.randomUUID();

        Step savedStep = new Step();
        savedStep.setId(stepId);
        savedStep.setName("Step One");
        savedStep.setStatus(StepStatus.PENDING);
        savedStep.setWorkflow(workflow);

        StepResponse stepResponse = new StepResponse(stepId, workflowId, "Step One", StepStatus.PENDING);
        String suggestion = "Suggested next step for 'Test Workflow' (step 2)";

        when(workflowValidator.requireWorkflow(workflowId)).thenReturn(workflow);
        when(stepRepository.save(any(Step.class))).thenReturn(savedStep);
        when(stepRepository.findByWorkflowId(workflowId)).thenReturn(List.of(savedStep));
        when(aiSuggester.suggestNextStep("Test Workflow", List.of("Step One"))).thenReturn(suggestion);
        when(workflowMapper.toStepResponse(savedStep)).thenReturn(stepResponse);

        AddStepResponse result = service.addStep(workflowId, request);

        assertThat(result.step()).isEqualTo(stepResponse);
        assertThat(result.suggestedNextStep()).isEqualTo(suggestion);
        verify(stepRepository).save(any(Step.class));
        verify(workflowRepository).save(workflow); // status transition
        assertThat(workflow.getStatus()).isEqualTo(WorkflowStatus.IN_PROGRESS);
        verify(eventPublisher).publishStepCreated(workflowId, stepId, "Step One");
        verify(notificationLogger).logStepAdded(stepId, "Step One", workflowId);
    }

    @Test
    void addStep_workflowNotFound_throwsWorkflowNotFoundException() {
        when(workflowValidator.requireWorkflow(workflowId))
                .thenThrow(new WorkflowNotFoundException(workflowId));

        assertThatThrownBy(() -> service.addStep(workflowId, new AddStepRequest("Step")))
                .isInstanceOf(WorkflowNotFoundException.class);

        verify(stepRepository, never()).save(any());
    }

    @Test
    void addStep_workflowAlreadyInProgress_doesNotSaveWorkflowAgain() {
        workflow.setStatus(WorkflowStatus.IN_PROGRESS);
        AddStepRequest request = new AddStepRequest("Step Two");
        UUID stepId = UUID.randomUUID();

        Step savedStep = new Step();
        savedStep.setId(stepId);
        savedStep.setName("Step Two");
        savedStep.setStatus(StepStatus.PENDING);
        savedStep.setWorkflow(workflow);

        StepResponse stepResponse = new StepResponse(stepId, workflowId, "Step Two", StepStatus.PENDING);

        when(workflowValidator.requireWorkflow(workflowId)).thenReturn(workflow);
        when(stepRepository.save(any(Step.class))).thenReturn(savedStep);
        when(stepRepository.findByWorkflowId(workflowId)).thenReturn(List.of(savedStep));
        when(aiSuggester.suggestNextStep(any(), any())).thenReturn("suggestion");
        when(workflowMapper.toStepResponse(savedStep)).thenReturn(stepResponse);

        service.addStep(workflowId, request);

        // workflowRepository.save should NOT be called since status is already IN_PROGRESS
        verify(workflowRepository, never()).save(any());
    }
}
