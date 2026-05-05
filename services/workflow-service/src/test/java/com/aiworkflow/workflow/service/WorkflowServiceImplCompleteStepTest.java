package com.aiworkflow.workflow.service;

import com.aiworkflow.workflow.dto.StepResponse;
import com.aiworkflow.workflow.dto.WorkflowResponse;
import com.aiworkflow.workflow.entity.Step;
import com.aiworkflow.workflow.entity.Workflow;
import com.aiworkflow.workflow.enums.StepStatus;
import com.aiworkflow.workflow.enums.WorkflowStatus;
import com.aiworkflow.workflow.event.EventPublisher;
import com.aiworkflow.workflow.exception.StepNotFoundException;
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
class WorkflowServiceImplCompleteStepTest {

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
    private UUID stepId;
    private Workflow workflow;
    private Step step;

    @BeforeEach
    void setUp() {
        workflowId = UUID.randomUUID();
        stepId = UUID.randomUUID();

        workflow = new Workflow();
        workflow.setId(workflowId);
        workflow.setName("Test Workflow");
        workflow.setStatus(WorkflowStatus.IN_PROGRESS);
        workflow.setSteps(new ArrayList<>());

        step = new Step();
        step.setId(stepId);
        step.setName("Step One");
        step.setStatus(StepStatus.PENDING);
        step.setWorkflow(workflow);
    }

    @Test
    void completeStep_stepNotFound_throwsStepNotFoundException() {
        when(workflowValidator.requireStepInWorkflow(workflowId, stepId))
                .thenThrow(new StepNotFoundException(stepId));

        assertThatThrownBy(() -> service.completeStep(workflowId, stepId))
                .isInstanceOf(StepNotFoundException.class);

        verify(stepRepository, never()).save(any());
    }

    @Test
    void completeStep_notAllStepsDone_workflowStaysInProgress() {
        WorkflowResponse expectedResponse = new WorkflowResponse(workflowId, "Test Workflow", WorkflowStatus.IN_PROGRESS, List.of());

        when(workflowValidator.requireStepInWorkflow(workflowId, stepId)).thenReturn(step);
        when(stepRepository.save(step)).thenReturn(step);
        // 1 incomplete step remains
        when(stepRepository.countByWorkflowIdAndStatusNot(workflowId, StepStatus.COMPLETED)).thenReturn(1L);
        when(workflowValidator.requireWorkflow(workflowId)).thenReturn(workflow);
        when(workflowMapper.toResponse(workflow)).thenReturn(expectedResponse);

        WorkflowResponse result = service.completeStep(workflowId, stepId);

        assertThat(result.status()).isEqualTo(WorkflowStatus.IN_PROGRESS);
        assertThat(step.getStatus()).isEqualTo(StepStatus.COMPLETED);
        // workflow should NOT be saved (no status change)
        verify(workflowRepository, never()).save(any());
        verify(eventPublisher).publishStepCompleted(workflowId, stepId);
        verify(notificationLogger).logStepCompleted(stepId, workflowId);
    }

    @Test
    void completeStep_allStepsDone_workflowTransitionsToCompleted() {
        WorkflowResponse expectedResponse = new WorkflowResponse(workflowId, "Test Workflow", WorkflowStatus.COMPLETED,
                List.of(new StepResponse(stepId, workflowId, "Step One", StepStatus.COMPLETED)));

        when(workflowValidator.requireStepInWorkflow(workflowId, stepId)).thenReturn(step);
        when(stepRepository.save(step)).thenReturn(step);
        // 0 incomplete steps — all done
        when(stepRepository.countByWorkflowIdAndStatusNot(workflowId, StepStatus.COMPLETED)).thenReturn(0L);
        when(workflowValidator.requireWorkflow(workflowId)).thenReturn(workflow);
        when(workflowMapper.toResponse(workflow)).thenReturn(expectedResponse);

        WorkflowResponse result = service.completeStep(workflowId, stepId);

        assertThat(result.status()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(workflow.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        verify(workflowRepository).save(workflow);
        verify(eventPublisher).publishStepCompleted(workflowId, stepId);
        verify(notificationLogger).logStepCompleted(stepId, workflowId);
    }
}
