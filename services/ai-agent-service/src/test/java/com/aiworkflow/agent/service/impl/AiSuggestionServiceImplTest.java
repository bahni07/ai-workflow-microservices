package com.aiworkflow.agent.service.impl;

import com.aiworkflow.agent.client.OpenAiClient;
import com.aiworkflow.agent.dto.SuggestionRequest;
import com.aiworkflow.agent.dto.SuggestionResponse;
import com.aiworkflow.agent.exception.OpenAiUnavailableException;
import com.aiworkflow.agent.model.FallbackReason;
import com.aiworkflow.agent.model.SuggestionSource;
import com.aiworkflow.agent.service.impl.AiSuggestionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiSuggestionServiceImplTest {

    @Mock OpenAiClient openAiClient;

    AiSuggestionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiSuggestionServiceImpl(openAiClient);
    }

    // 1. Client returns non-blank string → source=AI, suggestion equals returned string (trimmed, first line, max 200)
    @Test
    void suggest_clientReturnsNonBlank_sourceIsAI() {
        when(openAiClient.complete(anyString())).thenReturn("Send welcome email");

        SuggestionResponse response = service.suggest(new SuggestionRequest("Onboarding", List.of("Create account")));

        assertThat(response.source()).isEqualTo(SuggestionSource.AI);
        assertThat(response.suggestion()).isEqualTo("Send welcome email");
    }

    // 2. Client throws any RuntimeException → source=FALLBACK, suggestion non-blank
    @Test
    void suggest_clientThrowsRuntimeException_sourceIsFallback() {
        when(openAiClient.complete(anyString())).thenThrow(new RuntimeException("unexpected error"));

        SuggestionResponse response = service.suggest(new SuggestionRequest("Onboarding", List.of("Create account")));

        assertThat(response.source()).isEqualTo(SuggestionSource.FALLBACK);
        assertThat(response.suggestion()).isNotBlank();
    }

    // 3. Client throws OpenAiUnavailableException(MISSING_API_KEY) → source=FALLBACK
    @Test
    void suggest_clientThrowsMissingApiKey_sourceIsFallback() {
        when(openAiClient.complete(anyString()))
                .thenThrow(new OpenAiUnavailableException(FallbackReason.MISSING_API_KEY));

        SuggestionResponse response = service.suggest(new SuggestionRequest("Onboarding", List.of()));

        assertThat(response.source()).isEqualTo(SuggestionSource.FALLBACK);
        assertThat(response.suggestion()).isNotBlank();
    }

    // 4. Client throws ResourceAccessException → source=FALLBACK
    @Test
    void suggest_clientThrowsResourceAccessException_sourceIsFallback() {
        when(openAiClient.complete(anyString()))
                .thenThrow(new ResourceAccessException("Connection timed out"));

        SuggestionResponse response = service.suggest(new SuggestionRequest("Onboarding", List.of()));

        assertThat(response.source()).isEqualTo(SuggestionSource.FALLBACK);
        assertThat(response.suggestion()).isNotBlank();
    }

    // 5. Fallback suggestion formula: "Step N+1 for '<workflowName>'"
    @Test
    void suggest_fallback_formulaIsCorrect() {
        when(openAiClient.complete(anyString())).thenThrow(new RuntimeException("fail"));

        List<String> steps = List.of("Step A", "Step B");
        SuggestionResponse response = service.suggest(new SuggestionRequest("My Workflow", steps));

        assertThat(response.suggestion()).isEqualTo("Step 3 for 'My Workflow'");
    }

    // 6. Prompt uses last 10 steps only when list exceeds 10
    @Test
    void buildPrompt_moreThan10Steps_usesLast10Only() {
        List<String> steps = IntStream.rangeClosed(1, 15)
                .mapToObj(i -> "Step " + i)
                .toList();

        String prompt = service.buildPrompt("Workflow", steps);

        // Should contain steps 6-15 (last 10)
        assertThat(prompt).contains("Step 6");
        assertThat(prompt).contains("Step 15");
        // Should NOT contain steps 1-5
        assertThat(prompt).doesNotContain("Step 1\n");
        assertThat(prompt).doesNotContain("Step 2\n");
        assertThat(prompt).doesNotContain("Step 3\n");
        assertThat(prompt).doesNotContain("Step 4\n");
        assertThat(prompt).doesNotContain("Step 5\n");
    }

    // 7. Prompt contains workflowName
    @Test
    void buildPrompt_containsWorkflowName() {
        String prompt = service.buildPrompt("My Special Workflow", List.of("Step A"));

        assertThat(prompt).contains("My Special Workflow");
    }

    // 8. Prompt contains all step names when list is ≤10
    @Test
    void buildPrompt_tenOrFewerSteps_containsAllStepNames() {
        List<String> steps = List.of("Alpha", "Beta", "Gamma");

        String prompt = service.buildPrompt("Workflow", steps);

        assertThat(prompt).contains("Alpha");
        assertThat(prompt).contains("Beta");
        assertThat(prompt).contains("Gamma");
    }

    // 9. Prompt is non-blank when existingSteps is empty
    @Test
    void buildPrompt_emptySteps_isNonBlank() {
        String prompt = service.buildPrompt("Onboarding", Collections.emptyList());

        assertThat(prompt).isNotBlank();
    }

    // 10. Blank AI response (empty string) triggers EMPTY_RESPONSE fallback → source=FALLBACK
    @Test
    void suggest_blankAiResponse_sourceIsFallback() {
        when(openAiClient.complete(anyString())).thenReturn("   ");

        SuggestionResponse response = service.suggest(new SuggestionRequest("Onboarding", List.of()));

        assertThat(response.source()).isEqualTo(SuggestionSource.FALLBACK);
        assertThat(response.suggestion()).isNotBlank();
    }

    // 11. Multi-line AI response is trimmed to first line
    @Test
    void suggest_multiLineAiResponse_returnsFirstLineOnly() {
        when(openAiClient.complete(anyString())).thenReturn("First line\nSecond line\nThird line");

        SuggestionResponse response = service.suggest(new SuggestionRequest("Onboarding", List.of()));

        assertThat(response.source()).isEqualTo(SuggestionSource.AI);
        assertThat(response.suggestion()).isEqualTo("First line");
    }

    // 12. Response truncated to 200 chars when AI returns >200 char string
    @Test
    void suggest_aiResponseOver200Chars_isTruncatedTo200() {
        String longResponse = "A".repeat(250);
        when(openAiClient.complete(anyString())).thenReturn(longResponse);

        SuggestionResponse response = service.suggest(new SuggestionRequest("Onboarding", List.of()));

        assertThat(response.source()).isEqualTo(SuggestionSource.AI);
        assertThat(response.suggestion()).hasSize(200);
    }
}
