package com.aiworkflow.agent.exception;

import com.aiworkflow.agent.dto.SuggestionRequest;
import com.aiworkflow.agent.dto.SuggestionResponse;
import com.aiworkflow.agent.model.SuggestionSource;
import com.aiworkflow.agent.service.AiSuggestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for GlobalExceptionHandler using the real SuggestionController endpoint.
 * The controller is loaded via @WebMvcTest and the handler is imported explicitly.
 */
@WebMvcTest(controllers = com.aiworkflow.agent.controller.SuggestionController.class)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    @Autowired MockMvc mockMvc;
    @MockBean AiSuggestionService aiSuggestionService;

    // 1. MethodArgumentNotValidException → HTTP 400 with "Validation failed" in body
    @Test
    void validationException_returns400WithValidationFailed() throws Exception {
        mockMvc.perform(post("/suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workflowName":"","existingSteps":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));
    }

    // 2. HttpMessageNotReadableException → HTTP 400 with "Malformed request body" in body
    @Test
    void malformedJson_returns400WithMalformedRequestBody() throws Exception {
        mockMvc.perform(post("/suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bad json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed request body"));
    }

    // 3. Unhandled Exception → HTTP 500 with "Internal server error" in body
    @Test
    void unhandledException_returns500WithInternalServerError() throws Exception {
        when(aiSuggestionService.suggest(any())).thenThrow(new RuntimeException("unexpected"));

        mockMvc.perform(post("/suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workflowName":"Onboarding","existingSteps":[]}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal server error"));
    }

    // 4. No stack trace in any error response body
    @Test
    void errorResponse_doesNotContainStackTrace() throws Exception {
        mockMvc.perform(post("/suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workflowName":"","existingSteps":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("at com."))))
                .andExpect(content().string(not(containsString("StackTrace"))))
                .andExpect(content().string(not(containsString("Exception"))));
    }
}
