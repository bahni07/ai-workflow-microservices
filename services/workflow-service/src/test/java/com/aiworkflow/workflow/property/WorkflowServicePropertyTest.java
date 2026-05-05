package com.aiworkflow.workflow.property;

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
import com.aiworkflow.workflow.service.AISuggester;
import com.aiworkflow.workflow.service.WorkflowService;
import com.aiworkflow.workflow.service.impl.MockAISuggester;
import com.aiworkflow.workflow.service.impl.WorkflowServiceImpl;
import com.aiworkflow.workflow.validation.WorkflowValidator;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for WorkflowService using jqwik.
 * Uses manually created Mockito mocks (no @ExtendWith(MockitoExtension.class))
 * because jqwik manages its own lifecycle and does not support JUnit 5 extensions directly.
 */
class WorkflowServicePropertyTest {

    private WorkflowRepository workflowRepository;
    private StepRepository stepRepository;
    private WorkflowMapper workflowMapper;
    private AISuggester aiSuggester;
    private EventPublisher eventPublisher;
    private NotificationLogger notificationLogger;
    private WorkflowValidator workflowValidator;
    private WorkflowService service;

    @BeforeProperty
    void initMocks() {
        workflowRepository = Mockito.mock(WorkflowRepository.class);
        stepRepository = Mockito.mock(StepRepository.class);
        workflowMapper = Mockito.mock(WorkflowMapper.class);
        aiSuggester = Mockito.mock(AISuggester.class);
        eventPublisher = Mockito.mock(EventPublisher.class);
        notificationLogger = Mockito.mock(NotificationLogger.class);
        workflowValidator = Mockito.mock(WorkflowValidator.class);

        service = new WorkflowServiceImpl(
                workflowRepository,
                stepRepository,
                workflowMapper,
                aiSuggester,
                eventPublisher,
                notificationLogger,
                workflowValidator
        );
    }

    // Feature: workflow-service, Property 1: For any valid workflow name, createWorkflow returns a response
    // with the same name and status CREATED.
    // Validates: Requirements 1.1, 1.3, 1.4, 2.1
    @Property(tries = 10)
    void property1_createWorkflow_returnsMatchingNameAndCreatedStatus(
            @ForAll("validWorkflowNames") String name) {

        UUID id = UUID.randomUUID();

        Workflow savedWorkflow = new Workflow();
        savedWorkflow.setId(id);
        savedWorkflow.setName(name);
        savedWorkflow.setStatus(WorkflowStatus.CREATED);
        savedWorkflow.setSteps(new ArrayList<>());

        WorkflowResponse expectedResponse = new WorkflowResponse(id, name, WorkflowStatus.CREATED, List.of());

        when(workflowRepository.save(any(Workflow.class))).thenReturn(savedWorkflow);
        when(workflowMapper.toResponse(savedWorkflow)).thenReturn(expectedResponse);

        WorkflowResponse result = service.createWorkflow(new CreateWorkflowRequest(name));

        assertThat(result.name()).isEqualTo(name);
        assertThat(result.status()).isEqualTo(WorkflowStatus.CREATED);
    }

    @Provide
    Arbitrary<String> validWorkflowNames() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50);
    }

    // Feature: workflow-service, Property 2: For any blank/whitespace-only name, the @NotBlank constraint
    // on CreateWorkflowRequest is violated (validation at controller boundary returns 400).
    // Validates: Requirement 1.2
    @Property(tries = 10)
    void property2_blankWorkflowName_failsNotBlankConstraint(
            @ForAll("blankStrings") String blankName) {

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            CreateWorkflowRequest request = new CreateWorkflowRequest(blankName);
            Set<ConstraintViolation<CreateWorkflowRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
        }
    }

    @Provide
    Arbitrary<String> blankStrings() {
        // Generate strings composed only of whitespace characters
        return Arbitraries.strings()
                .withChars(' ', '\t', '\n', '\r')
                .ofMinLength(0)
                .ofMaxLength(20);
    }

    // Feature: workflow-service, Property 3: For any valid workflow name and step name, after addStep on a
    // CREATED workflow, the returned AddStepResponse has a step with status PENDING and the workflow
    // status transitions to IN_PROGRESS.
    // Validates: Requirements 3.1, 3.4, 3.5, 2.3
    @Property(tries = 10)
    void property3_addStep_transitionsWorkflowToInProgressWithPendingStep(
            @ForAll("validWorkflowNames") String workflowName,
            @ForAll("validWorkflowNames") String stepName) {

        UUID workflowId = UUID.randomUUID();
        UUID stepId = UUID.randomUUID();

        Workflow workflow = new Workflow();
        workflow.setId(workflowId);
        workflow.setName(workflowName);
        workflow.setStatus(WorkflowStatus.CREATED);
        workflow.setSteps(new ArrayList<>());

        Step savedStep = new Step();
        savedStep.setId(stepId);
        savedStep.setName(stepName);
        savedStep.setStatus(StepStatus.PENDING);
        savedStep.setWorkflow(workflow);

        StepResponse stepResponse = new StepResponse(stepId, workflowId, stepName, StepStatus.PENDING);

        when(workflowValidator.requireWorkflow(workflowId)).thenReturn(workflow);
        when(stepRepository.save(any(Step.class))).thenReturn(savedStep);
        when(stepRepository.findByWorkflowId(workflowId)).thenReturn(List.of(savedStep));
        when(aiSuggester.suggestNextStep(any(), any())).thenReturn("some suggestion");
        when(workflowMapper.toStepResponse(savedStep)).thenReturn(stepResponse);

        AddStepResponse result = service.addStep(workflowId, new AddStepRequest(stepName));

        assertThat(result.step().status()).isEqualTo(StepStatus.PENDING);
        assertThat(workflow.getStatus()).isEqualTo(WorkflowStatus.IN_PROGRESS);
    }

    // Feature: workflow-service, Property 4: For any valid workflow name and any number of existing steps,
    // MockAISuggester.suggestNextStep always returns a non-null, non-blank string.
    // Validates: Requirements 4.1, 4.2, 4.3
    @Property(tries = 10)
    void property4_mockAISuggester_alwaysReturnsNonBlankSuggestion(
            @ForAll("validWorkflowNames") String workflowName,
            @ForAll("stepCountArbitrary") int stepCount) {

        MockAISuggester realSuggester = new MockAISuggester();
        List<String> existingSteps = Collections.nCopies(stepCount, "step");

        String result = realSuggester.suggestNextStep(workflowName, existingSteps);

        assertThat(result).isNotNull();
        assertThat(result).isNotBlank();
    }

    @Provide
    Arbitrary<Integer> stepCountArbitrary() {
        return Arbitraries.integers().between(0, 10);
    }

    // Feature: workflow-service, Property 5: For any UUID that does not correspond to a persisted workflow,
    // getWorkflow throws WorkflowNotFoundException.
    // Validates: Requirements 2.2, 3.2
    @Property(tries = 10)
    void property5_nonExistentWorkflowId_throwsWorkflowNotFoundException(
            @ForAll("randomUUIDs") UUID randomId) {

        when(workflowValidator.requireWorkflow(randomId))
                .thenThrow(new WorkflowNotFoundException(randomId));

        assertThatThrownBy(() -> service.getWorkflow(randomId))
                .isInstanceOf(WorkflowNotFoundException.class);
    }

    @Provide
    Arbitrary<UUID> randomUUIDs() {
        return Arbitraries.create(UUID::randomUUID);
    }

    // Feature: workflow-service, Property 6: For any workflow name, even when eventPublisher throws on
    // publishWorkflowCreated, createWorkflow still returns a valid WorkflowResponse.
    // Validates: Requirement 5.3 (6.4 in requirements)
    @Property(tries = 10)
    void property6_eventPublisherThrows_createWorkflowStillSucceeds(
            @ForAll("validWorkflowNames") String name) {

        UUID id = UUID.randomUUID();

        Workflow savedWorkflow = new Workflow();
        savedWorkflow.setId(id);
        savedWorkflow.setName(name);
        savedWorkflow.setStatus(WorkflowStatus.CREATED);
        savedWorkflow.setSteps(new ArrayList<>());

        WorkflowResponse expectedResponse = new WorkflowResponse(id, name, WorkflowStatus.CREATED, List.of());

        when(workflowRepository.save(any(Workflow.class))).thenReturn(savedWorkflow);
        doThrow(new RuntimeException("Kafka unavailable"))
                .when(eventPublisher).publishWorkflowCreated(any(), any());
        when(workflowMapper.toResponse(savedWorkflow)).thenReturn(expectedResponse);

        // Must not throw — event publisher failure is swallowed
        WorkflowResponse result = service.createWorkflow(new CreateWorkflowRequest(name));

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo(name);
    }

    // Feature: workflow-service, Property 7: For any N between 1 and 5, after completing all N steps
    // one by one, the workflow status is COMPLETED.
    // Validates: Requirements 4.3
    @Property(tries = 10)
    void property7_completingAllSteps_transitionsWorkflowToCompleted(
            @ForAll("stepCounts") int n) {

        UUID workflowId = UUID.randomUUID();

        Workflow workflow = new Workflow();
        workflow.setId(workflowId);
        workflow.setName("Test Workflow");
        workflow.setStatus(WorkflowStatus.IN_PROGRESS);
        workflow.setSteps(new ArrayList<>());

        // Create N steps
        List<UUID> stepIds = new ArrayList<>();
        List<Step> steps = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            UUID stepId = UUID.randomUUID();
            stepIds.add(stepId);
            Step step = new Step();
            step.setId(stepId);
            step.setName("Step " + (i + 1));
            step.setStatus(StepStatus.PENDING);
            step.setWorkflow(workflow);
            steps.add(step);
        }

        // For each step completion, set up mocks
        for (int i = 0; i < n; i++) {
            UUID stepId = stepIds.get(i);
            Step step = steps.get(i);

            // On the last step, countByWorkflowIdAndStatusNot returns 0 (all done)
            // On earlier steps, return remaining count > 0
            long remainingAfter = n - 1 - i;

            when(workflowValidator.requireStepInWorkflow(workflowId, stepId)).thenReturn(step);
            when(stepRepository.save(step)).thenReturn(step);
            when(stepRepository.countByWorkflowIdAndStatusNot(workflowId, StepStatus.COMPLETED))
                    .thenReturn(remainingAfter);

            WorkflowResponse responseForThisCall = new WorkflowResponse(
                    workflowId, "Test Workflow",
                    remainingAfter == 0 ? WorkflowStatus.COMPLETED : WorkflowStatus.IN_PROGRESS,
                    List.of()
            );
            when(workflowValidator.requireWorkflow(workflowId)).thenReturn(workflow);
            when(workflowMapper.toResponse(workflow)).thenReturn(responseForThisCall);
        }

        WorkflowResponse lastResult = null;
        for (int i = 0; i < n; i++) {
            lastResult = service.completeStep(workflowId, stepIds.get(i));
        }

        // After the final completeStep, workflow status should be COMPLETED
        assertThat(workflow.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        verify(workflowRepository, atLeastOnce()).save(argThat(w -> w.getStatus() == WorkflowStatus.COMPLETED));
    }

    @Provide
    Arbitrary<Integer> stepCounts() {
        return Arbitraries.integers().between(1, 5);
    }
}
