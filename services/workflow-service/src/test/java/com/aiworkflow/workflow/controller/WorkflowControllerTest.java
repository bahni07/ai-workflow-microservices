package com.aiworkflow.workflow.controller;

import com.aiworkflow.workflow.dto.*;
import com.aiworkflow.workflow.enums.StepStatus;
import com.aiworkflow.workflow.enums.WorkflowStatus;
import com.aiworkflow.workflow.exception.GlobalExceptionHandler;
import com.aiworkflow.workflow.exception.WorkflowNotFoundException;
import com.aiworkflow.workflow.service.WorkflowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WorkflowController.class)
class WorkflowControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean WorkflowService workflowService;

    private final UUID workflowId = UUID.randomUUID();
    private final UUID stepId = UUID.randomUUID();

    @Test
    void postWorkflows_validBody_returns201() throws Exception {
        WorkflowResponse response = new WorkflowResponse(workflowId, "My Workflow", WorkflowStatus.CREATED, List.of());
        when(workflowService.createWorkflow(any())).thenReturn(response);

        mockMvc.perform(post("/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "My Workflow"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(workflowId.toString()))
                .andExpect(jsonPath("$.name").value("My Workflow"))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void postWorkflows_blankName_returns400() throws Exception {
        mockMvc.perform(post("/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));
    }

    @Test
    void postWorkflows_missingName_returns400() throws Exception {
        mockMvc.perform(post("/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));
    }

    @Test
    void getWorkflow_existingId_returns200() throws Exception {
        WorkflowResponse response = new WorkflowResponse(workflowId, "My Workflow", WorkflowStatus.CREATED, List.of());
        when(workflowService.getWorkflow(workflowId)).thenReturn(response);

        mockMvc.perform(get("/workflows/{id}", workflowId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(workflowId.toString()))
                .andExpect(jsonPath("$.name").value("My Workflow"));
    }

    @Test
    void getWorkflow_notFound_returns404() throws Exception {
        when(workflowService.getWorkflow(workflowId))
                .thenThrow(new WorkflowNotFoundException(workflowId));

        mockMvc.perform(get("/workflows/{id}", workflowId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Workflow not found"));
    }

    @Test
    void postStep_validBody_returns201() throws Exception {
        StepResponse stepResponse = new StepResponse(stepId, workflowId, "Step One", StepStatus.PENDING);
        AddStepResponse response = new AddStepResponse(stepResponse, "Suggested next step for 'My Workflow' (step 2)");

        when(workflowService.addStep(eq(workflowId), any())).thenReturn(response);

        mockMvc.perform(post("/workflows/{id}/steps", workflowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Step One"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.step.id").value(stepId.toString()))
                .andExpect(jsonPath("$.step.name").value("Step One"))
                .andExpect(jsonPath("$.suggestedNextStep").isNotEmpty());
    }

    @Test
    void postCompleteStep_validIds_returns200() throws Exception {
        WorkflowResponse response = new WorkflowResponse(workflowId, "My Workflow", WorkflowStatus.IN_PROGRESS,
                List.of(new StepResponse(stepId, workflowId, "Step One", StepStatus.COMPLETED)));

        when(workflowService.completeStep(workflowId, stepId)).thenReturn(response);

        mockMvc.perform(post("/workflows/{workflowId}/steps/{stepId}/complete", workflowId, stepId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(workflowId.toString()));
    }
}
