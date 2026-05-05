package com.aiworkflow.agent.controller;

import com.aiworkflow.agent.dto.SuggestionRequest;
import com.aiworkflow.agent.dto.SuggestionResponse;
import com.aiworkflow.agent.exception.GlobalExceptionHandler;
import com.aiworkflow.agent.model.SuggestionSource;
import com.aiworkflow.agent.service.AiSuggestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SuggestionController.class)
@Import(GlobalExceptionHandler.class)
class SuggestionControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean AiSuggestionService aiSuggestionService;

    // 1. Valid request returns HTTP 200 with suggestion and source fields
    @Test
    void suggest_validRequest_returns200WithSuggestionAndSource() throws Exception {
        SuggestionResponse response = new SuggestionResponse("Send welcome email", SuggestionSource.AI);
        when(aiSuggestionService.suggest(any())).thenReturn(response);

        mockMvc.perform(post("/suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workflowName":"Onboarding","existingSteps":["Create account"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestion").value("Send welcome email"))
                .andExpect(jsonPath("$.source").value("AI"));
    }

    // 2. Blank workflowName returns HTTP 400
    @Test
    void suggest_blankWorkflowName_returns400() throws Exception {
        mockMvc.perform(post("/suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workflowName":"   ","existingSteps":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));
    }

    // 3. Empty string workflowName returns HTTP 400
    @Test
    void suggest_emptyWorkflowName_returns400() throws Exception {
        mockMvc.perform(post("/suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workflowName":"","existingSteps":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));
    }

    // 4. Malformed JSON body returns HTTP 400
    @Test
    void suggest_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed request body"));
    }

    // 5. Null existingSteps is treated as empty list → HTTP 200
    @Test
    void suggest_nullExistingSteps_treatedAsEmptyList_returns200() throws Exception {
        SuggestionResponse response = new SuggestionResponse("Step 1 for 'Onboarding'", SuggestionSource.FALLBACK);
        when(aiSuggestionService.suggest(any())).thenReturn(response);

        mockMvc.perform(post("/suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workflowName":"Onboarding"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestion").isNotEmpty())
                .andExpect(jsonPath("$.source").isNotEmpty());
    }
}
