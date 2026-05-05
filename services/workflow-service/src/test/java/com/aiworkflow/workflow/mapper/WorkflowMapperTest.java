package com.aiworkflow.workflow.mapper;

import com.aiworkflow.workflow.dto.StepResponse;
import com.aiworkflow.workflow.dto.WorkflowResponse;
import com.aiworkflow.workflow.entity.Step;
import com.aiworkflow.workflow.entity.Workflow;
import com.aiworkflow.workflow.enums.StepStatus;
import com.aiworkflow.workflow.enums.WorkflowStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowMapperTest {

    private final WorkflowMapper mapper = new WorkflowMapper();

    @Test
    void toResponse_mapsIdNameStatusCorrectly() {
        UUID id = UUID.randomUUID();
        Workflow workflow = workflow(id, "My Workflow", WorkflowStatus.CREATED, List.of());

        WorkflowResponse response = mapper.toResponse(workflow);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.name()).isEqualTo("My Workflow");
        assertThat(response.status()).isEqualTo(WorkflowStatus.CREATED);
    }

    @Test
    void toResponse_mapsEmptyStepsList() {
        Workflow workflow = workflow(UUID.randomUUID(), "W", WorkflowStatus.CREATED, List.of());

        WorkflowResponse response = mapper.toResponse(workflow);

        assertThat(response.steps()).isEmpty();
    }

    @Test
    void toResponse_mapsStepsListCorrectly() {
        UUID workflowId = UUID.randomUUID();
        UUID stepId = UUID.randomUUID();

        Workflow workflow = workflow(workflowId, "W", WorkflowStatus.IN_PROGRESS, List.of());
        Step step = step(stepId, workflow, "Step One", StepStatus.PENDING);
        workflow.setSteps(List.of(step));

        WorkflowResponse response = mapper.toResponse(workflow);

        assertThat(response.steps()).hasSize(1);
        assertThat(response.steps().get(0).id()).isEqualTo(stepId);
        assertThat(response.steps().get(0).name()).isEqualTo("Step One");
        assertThat(response.steps().get(0).status()).isEqualTo(StepStatus.PENDING);
    }

    @Test
    void toStepResponse_mapsAllFieldsCorrectly() {
        UUID workflowId = UUID.randomUUID();
        UUID stepId = UUID.randomUUID();

        Workflow workflow = workflow(workflowId, "W", WorkflowStatus.IN_PROGRESS, List.of());
        Step step = step(stepId, workflow, "Step One", StepStatus.COMPLETED);

        StepResponse response = mapper.toStepResponse(step);

        assertThat(response.id()).isEqualTo(stepId);
        assertThat(response.workflowId()).isEqualTo(workflowId);
        assertThat(response.name()).isEqualTo("Step One");
        assertThat(response.status()).isEqualTo(StepStatus.COMPLETED);
    }

    // --- helpers ---

    private Workflow workflow(UUID id, String name, WorkflowStatus status, List<Step> steps) {
        Workflow w = new Workflow();
        w.setId(id);
        w.setName(name);
        w.setStatus(status);
        w.setSteps(new java.util.ArrayList<>(steps));
        return w;
    }

    private Step step(UUID id, Workflow workflow, String name, StepStatus status) {
        Step s = new Step();
        s.setId(id);
        s.setWorkflow(workflow);
        s.setName(name);
        s.setStatus(status);
        return s;
    }
}
