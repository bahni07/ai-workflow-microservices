package com.aiworkflow.agent.controller;

import com.aiworkflow.agent.dto.SuggestionRequest;
import com.aiworkflow.agent.dto.SuggestionResponse;
import com.aiworkflow.agent.service.AiSuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;

@RestController
@RequestMapping("/suggestions")
public class SuggestionController {

    private final AiSuggestionService aiSuggestionService;

    public SuggestionController(AiSuggestionService aiSuggestionService) {
        this.aiSuggestionService = aiSuggestionService;
    }

    @Operation(summary = "Generate next-step suggestion for a workflow")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Suggestion generated successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failure — blank workflowName or invalid request"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PostMapping
    public ResponseEntity<SuggestionResponse> suggest(@Valid @RequestBody SuggestionRequest request) {
        if (request.existingSteps() == null) {
            request = new SuggestionRequest(request.workflowName(), Collections.emptyList());
        }
        SuggestionResponse response = aiSuggestionService.suggest(request);
        return ResponseEntity.ok(response);
    }
}
