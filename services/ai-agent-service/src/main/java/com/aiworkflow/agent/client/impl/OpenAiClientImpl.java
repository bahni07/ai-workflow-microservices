package com.aiworkflow.agent.client.impl;

import com.aiworkflow.agent.client.OpenAiClient;
import com.aiworkflow.agent.client.model.ChatCompletionRequest;
import com.aiworkflow.agent.client.model.ChatCompletionResponse;
import com.aiworkflow.agent.client.model.ChatMessage;
import com.aiworkflow.agent.config.OpenAiProperties;
import com.aiworkflow.agent.exception.OpenAiUnavailableException;
import com.aiworkflow.agent.model.FallbackReason;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class OpenAiClientImpl implements OpenAiClient {

    private final OpenAiProperties openAiProperties;
    private final RestTemplate restTemplate;

    public OpenAiClientImpl(OpenAiProperties openAiProperties, RestTemplate restTemplate) {
        this.openAiProperties = openAiProperties;
        this.restTemplate = restTemplate;
    }

    @Override
    public String complete(String prompt) {
        if (openAiProperties.getApiKey() == null || openAiProperties.getApiKey().isBlank()) {
            throw new OpenAiUnavailableException(FallbackReason.MISSING_API_KEY);
        }
        try {
            return doComplete(prompt);
        } catch (ResourceAccessException e) {
            // retry once on transient network failure
            return doComplete(prompt);
        }
    }

    private String doComplete(String prompt) {
        ChatCompletionRequest request = new ChatCompletionRequest(
                openAiProperties.getModel(),
                List.of(new ChatMessage("user", prompt)),
                openAiProperties.getTemperature(),
                openAiProperties.getMaxTokens()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + openAiProperties.getApiKey());

        HttpEntity<ChatCompletionRequest> entity = new HttpEntity<>(request, headers);

        ChatCompletionResponse response = restTemplate.postForObject(
                openAiProperties.getBaseUrl(),
                entity,
                ChatCompletionResponse.class
        );

        return response.choices().get(0).message().content();
    }
}
