package com.aiworkflow.agent.service.impl;

import com.aiworkflow.agent.client.OpenAiClient;
import com.aiworkflow.agent.controller.SuggestionController;
import com.aiworkflow.agent.dto.SuggestionRequest;
import com.aiworkflow.agent.dto.SuggestionResponse;
import com.aiworkflow.agent.exception.GlobalExceptionHandler;
import com.aiworkflow.agent.model.SuggestionSource;
import com.aiworkflow.agent.service.AiSuggestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Property-based tests for the AI Agent Service using jqwik.
 * Package: com.aiworkflow.agent.service.impl (same package as AiSuggestionServiceImpl
 * to access package-private buildPrompt method).
 */
class AiAgentServicePropertyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // -------------------------------------------------------------------------
    // Property 1: Suggestion is always non-blank
    // Feature: ai-agent-service, Property 1: Suggestion is always non-blank
    // Validates: Requirements 1.1, 1.4, 1.6
    // -------------------------------------------------------------------------

    /**
     * For any valid SuggestionRequest (non-blank workflowName, any existingSteps list),
     * when OpenAiClient throws (simulating unavailability), the response has non-blank
     * suggestion and non-null source.
     *
     * Validates: Requirements 1.1, 1.4, 1.6
     */
    @Property(tries = 100)
    void property1_suggestionIsAlwaysNonBlank(
            @ForAll @NotBlank @StringLength(max = 255) String workflowName,
            @ForAll @Size(max = 50) List<String> steps) {

        OpenAiClient mockClient = Mockito.mock(OpenAiClient.class);
        when(mockClient.complete(any())).thenThrow(new RuntimeException("simulated unavailability"));

        AiSuggestionServiceImpl service = new AiSuggestionServiceImpl(mockClient);
        SuggestionRequest request = new SuggestionRequest(workflowName, steps);

        SuggestionResponse response = service.suggest(request);

        assertThat(response.suggestion()).isNotNull().isNotBlank();
        assertThat(response.source()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Property 2: Fallback is used when OpenAI is unavailable
    // Feature: ai-agent-service, Property 2: Fallback is used when OpenAI is unavailable
    // Validates: Requirements 1.3, 4.1, 4.2, 4.3, 4.4
    // -------------------------------------------------------------------------

    /**
     * For any valid SuggestionRequest, when OpenAiClient throws any exception,
     * response has source=FALLBACK and non-blank suggestion.
     *
     * Validates: Requirements 1.3, 4.1, 4.2, 4.3, 4.4
     */
    @Property(tries = 100)
    void property2_fallbackUsedWhenOpenAiUnavailable(
            @ForAll @NotBlank @StringLength(max = 255) String workflowName,
            @ForAll @Size(max = 50) List<String> steps) {

        OpenAiClient mockClient = Mockito.mock(OpenAiClient.class);
        when(mockClient.complete(any())).thenThrow(new RuntimeException("simulated failure"));

        AiSuggestionServiceImpl service = new AiSuggestionServiceImpl(mockClient);
        SuggestionRequest request = new SuggestionRequest(workflowName, steps);

        SuggestionResponse response = service.suggest(request);

        assertThat(response.source()).isEqualTo(SuggestionSource.FALLBACK);
        assertThat(response.suggestion()).isNotBlank();
    }

    // -------------------------------------------------------------------------
    // Property 3: AI source is used when OpenAI responds
    // Feature: ai-agent-service, Property 3: AI source is used when OpenAI responds
    // Validates: Requirements 1.2
    // -------------------------------------------------------------------------

    /**
     * For any valid SuggestionRequest, when OpenAiClient returns a non-blank string,
     * response has source=AI and suggestion is non-blank.
     *
     * Validates: Requirements 1.2
     */
    @Property(tries = 100)
    void property3_aiSourceUsedWhenOpenAiResponds(
            @ForAll @NotBlank @StringLength(max = 255) String workflowName,
            @ForAll @Size(max = 50) List<String> steps,
            @ForAll @NotBlank @StringLength(min = 1, max = 200) String aiResponse) {

        OpenAiClient mockClient = Mockito.mock(OpenAiClient.class);
        // Return a single-line non-blank response so sanitization keeps it as AI
        String singleLine = aiResponse.replace("\n", " ").replace("\r", " ").trim();
        Assume.that(!singleLine.isBlank());
        when(mockClient.complete(any())).thenReturn(singleLine);

        AiSuggestionServiceImpl service = new AiSuggestionServiceImpl(mockClient);
        SuggestionRequest request = new SuggestionRequest(workflowName, steps);

        SuggestionResponse response = service.suggest(request);

        assertThat(response.source()).isEqualTo(SuggestionSource.AI);
        assertThat(response.suggestion()).isNotBlank();
    }

    // -------------------------------------------------------------------------
    // Property 4: Prompt contains full workflow context
    // Feature: ai-agent-service, Property 4: Prompt contains full workflow context
    // Validates: Requirements 2.1, 2.2, 2.3
    // -------------------------------------------------------------------------

    /**
     * For any non-blank workflowName and any list of step names (0-10 items),
     * the prompt contains workflowName and all step names.
     *
     * Validates: Requirements 2.1, 2.2, 2.3
     */
    @Property(tries = 100)
    void property4_promptContainsFullWorkflowContext(
            @ForAll @NotBlank @StringLength(max = 255) String workflowName,
            @ForAll @Size(max = 10) List<@NotBlank String> steps) {

        OpenAiClient mockClient = Mockito.mock(OpenAiClient.class);
        AiSuggestionServiceImpl service = new AiSuggestionServiceImpl(mockClient);

        String prompt = service.buildPrompt(workflowName, steps);

        assertThat(prompt).isNotBlank();
        assertThat(prompt).contains(workflowName);
        for (String step : steps) {
            assertThat(prompt).contains(step);
        }
    }

    // -------------------------------------------------------------------------
    // Property 5: Validation rejects blank workflowName
    // Feature: ai-agent-service, Property 5: Validation rejects blank workflowName
    // Validates: Requirements 5.1
    // -------------------------------------------------------------------------

    /**
     * For any blank string (empty or whitespace-only) as workflowName,
     * POST /suggestions returns HTTP 400.
     *
     * Validates: Requirements 5.1
     */
    @Property(tries = 100)
    void property5_validationRejectsBlankWorkflowName(
            @ForAll("blankStrings") String blankName) throws Exception {

        AiSuggestionService mockService = Mockito.mock(AiSuggestionService.class);
        SuggestionController controller = new SuggestionController(mockService);
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        String body = objectMapper.writeValueAsString(
                new SuggestionRequest(blankName, Collections.emptyList()));

        mockMvc.perform(post("/suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        // Service must NOT have been called
        Mockito.verify(mockService, Mockito.never()).suggest(any());
    }

    @Provide
    Arbitrary<String> blankStrings() {
        // Generate empty string or strings composed entirely of whitespace characters
        Arbitrary<String> empty = Arbitraries.just("");
        Arbitrary<String> whitespace = Arbitraries.strings()
                .withChars(' ', '\t', '\n', '\r')
                .ofMinLength(1)
                .ofMaxLength(50);
        return Arbitraries.oneOf(empty, whitespace);
    }

    // -------------------------------------------------------------------------
    // Property 6: Empty steps list is valid
    // Feature: ai-agent-service, Property 6: Empty steps list is valid
    // Validates: Requirements 1.5, 5.2
    // -------------------------------------------------------------------------

    /**
     * For any non-blank workflowName, POST /suggestions with empty existingSteps
     * returns HTTP 200 with non-blank suggestion.
     *
     * Validates: Requirements 1.5, 5.2
     */
    @Property(tries = 100)
    void property6_emptyStepsListIsValid(
            @ForAll @NotBlank @StringLength(max = 255) String workflowName) throws Exception {

        AiSuggestionService mockService = Mockito.mock(AiSuggestionService.class);
        String expectedSuggestion = "Step 1 for '" + workflowName + "'";
        when(mockService.suggest(any()))
                .thenReturn(new SuggestionResponse(expectedSuggestion, SuggestionSource.FALLBACK));

        SuggestionController controller = new SuggestionController(mockService);
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        String body = objectMapper.writeValueAsString(
                new SuggestionRequest(workflowName, Collections.emptyList()));

        mockMvc.perform(post("/suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------
    // Property 7: Response structure invariant
    // Feature: ai-agent-service, Property 7: Response structure invariant
    // Validates: Requirements 1.6, 4.3
    // -------------------------------------------------------------------------

    /**
     * For any valid SuggestionRequest, response always has non-null suggestion and
     * non-null source, and source is exactly AI or FALLBACK.
     * Tests both cases: when client throws (FALLBACK) and when client returns value (AI).
     *
     * Validates: Requirements 1.6, 4.3
     */
    @Property(tries = 100)
    void property7_responseStructureInvariant(
            @ForAll @NotBlank @StringLength(max = 255) String workflowName,
            @ForAll @Size(max = 50) List<String> steps,
            @ForAll boolean clientThrows) {

        OpenAiClient mockClient = Mockito.mock(OpenAiClient.class);

        if (clientThrows) {
            when(mockClient.complete(any())).thenThrow(new RuntimeException("simulated failure"));
        } else {
            when(mockClient.complete(any())).thenReturn("A valid AI step");
        }

        AiSuggestionServiceImpl service = new AiSuggestionServiceImpl(mockClient);
        SuggestionRequest request = new SuggestionRequest(workflowName, steps);

        SuggestionResponse response = service.suggest(request);

        assertThat(response.suggestion()).isNotNull();
        assertThat(response.source()).isNotNull();
        assertThat(response.source()).isIn(SuggestionSource.AI, SuggestionSource.FALLBACK);

        if (clientThrows) {
            assertThat(response.source()).isEqualTo(SuggestionSource.FALLBACK);
        } else {
            assertThat(response.source()).isEqualTo(SuggestionSource.AI);
        }
    }
}
