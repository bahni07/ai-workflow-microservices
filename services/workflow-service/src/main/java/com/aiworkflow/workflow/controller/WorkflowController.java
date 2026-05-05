package com.aiworkflow.workflow.controller;

import com.aiworkflow.workflow.dto.*;
import com.aiworkflow.workflow.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/workflows")
@Tag(name = "Workflows", description = "Workflow lifecycle management")
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @Operation(summary = "Create a new workflow")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Workflow created"),
        @ApiResponse(responseCode = "400", description = "Validation error")
    })
    @PostMapping
    public ResponseEntity<WorkflowResponse> createWorkflow(@Valid @RequestBody CreateWorkflowRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workflowService.createWorkflow(request));
    }

    @Operation(summary = "Get a workflow by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Workflow found"),
        @ApiResponse(responseCode = "404", description = "Workflow not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<WorkflowResponse> getWorkflow(@PathVariable UUID id) {
        return ResponseEntity.ok(workflowService.getWorkflow(id));
    }

    @Operation(summary = "Add a step to a workflow")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Step added"),
        @ApiResponse(responseCode = "400", description = "Validation error"),
        @ApiResponse(responseCode = "404", description = "Workflow not found")
    })
    @PostMapping("/{id}/steps")
    public ResponseEntity<AddStepResponse> addStep(@PathVariable UUID id,
                                                    @Valid @RequestBody AddStepRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workflowService.addStep(id, request));
    }

    @Operation(summary = "Complete a step")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Step completed"),
        @ApiResponse(responseCode = "404", description = "Workflow or step not found")
    })
    @PostMapping("/{workflowId}/steps/{stepId}/complete")
    public ResponseEntity<WorkflowResponse> completeStep(@PathVariable UUID workflowId,
                                                          @PathVariable UUID stepId) {
        return ResponseEntity.ok(workflowService.completeStep(workflowId, stepId));
    }
}
