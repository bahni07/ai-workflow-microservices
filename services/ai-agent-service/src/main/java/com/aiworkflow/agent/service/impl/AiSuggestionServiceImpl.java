package com.aiworkflow.agent.service.impl;

import com.aiworkflow.agent.client.OpenAiClient;
import com.aiworkflow.agent.dto.SuggestionRequest;
import com.aiworkflow.agent.dto.SuggestionResponse;
import com.aiworkflow.agent.exception.OpenAiUnavailableException;
import com.aiworkflow.agent.model.FallbackReason;
import com.aiworkflow.agent.model.SuggestionSource;
import com.aiworkflow.agent.service.AiSuggestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.util.Collections;
import java.util.List;

@Service
public class AiSuggestionServiceImpl implements AiSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(AiSuggestionServiceImpl.class);

    private final OpenAiClient openAiClient;

    public AiSuggestionServiceImpl(OpenAiClient openAiClient) {
        this.openAiClient = openAiClient;
    }

    String buildPrompt(String workflowName, List<String> existingSteps) {
        int size = existingSteps.size();
        List<String> lastTen = existingSteps.subList(Math.max(0, size - 10), size);

        if (lastTen.isEmpty()) {
            return "Workflow: " + workflowName + "\nWhat should be the first step? Reply with only the step name.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Workflow: ").append(workflowName).append("\nExisting steps:\n");
        for (int i = 0; i < lastTen.size(); i++) {
            sb.append(i + 1).append(". ").append(lastTen.get(i)).append("\n");
        }
        sb.append("What should be the next step? Reply with only the step name.");
        return sb.toString();
    }

    String fallbackSuggestion(SuggestionRequest request) {
        List<String> steps = request.existingSteps() != null ? request.existingSteps() : Collections.emptyList();
        return "Step " + (steps.size() + 1) + " for '" + request.workflowName() + "'";
    }

    @Override
    public SuggestionResponse suggest(SuggestionRequest request) {
        List<String> steps = request.existingSteps() != null ? request.existingSteps() : Collections.emptyList();
        String workflowName = request.workflowName();

        String prompt = buildPrompt(workflowName, steps);
        SuggestionSource source;
        String suggestion;

        try {
            String raw = openAiClient.complete(prompt);
            String sanitized = raw == null ? "" : raw.trim().split("\n")[0];
            if (sanitized.length() > 200) {
                sanitized = sanitized.substring(0, 200);
            }

            if (!sanitized.isBlank()) {
                suggestion = sanitized;
                source = SuggestionSource.AI;
            } else {
                FallbackReason reason = FallbackReason.EMPTY_RESPONSE;
                log.warn("Fallback triggered: reason={}, message={}", reason, "AI returned blank response");
                suggestion = fallbackSuggestion(request);
                source = SuggestionSource.FALLBACK;
            }
        } catch (Exception ex) {
            FallbackReason reason = categorizeReason(ex);
            log.warn("Fallback triggered: reason={}, message={}", reason, ex.getMessage());
            suggestion = fallbackSuggestion(request);
            source = SuggestionSource.FALLBACK;
        }

        log.info("Suggestion: workflowName={}, stepCount={}, source={}", workflowName, steps.size(), source);
        return new SuggestionResponse(suggestion, source);
    }

    private FallbackReason categorizeReason(Exception ex) {
        if (ex instanceof OpenAiUnavailableException oaue
                && oaue.getFallbackReason() == FallbackReason.MISSING_API_KEY) {
            return FallbackReason.MISSING_API_KEY;
        }
        if (ex instanceof ResourceAccessException) {
            return FallbackReason.TIMEOUT;
        }
        return FallbackReason.API_ERROR;
    }
}
