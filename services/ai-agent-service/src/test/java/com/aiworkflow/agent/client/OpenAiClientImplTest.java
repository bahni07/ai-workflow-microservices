package com.aiworkflow.agent.client;

import com.aiworkflow.agent.client.impl.OpenAiClientImpl;
import com.aiworkflow.agent.client.model.ChatCompletionRequest;
import com.aiworkflow.agent.client.model.ChatCompletionResponse;
import com.aiworkflow.agent.client.model.ChatMessage;
import com.aiworkflow.agent.client.model.Choice;
import com.aiworkflow.agent.config.OpenAiProperties;
import com.aiworkflow.agent.exception.OpenAiUnavailableException;
import com.aiworkflow.agent.model.FallbackReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpenAiClientImplTest {

    @Mock RestTemplate restTemplate;
    @Mock OpenAiProperties openAiProperties;

    OpenAiClientImpl client;

    @BeforeEach
    void setUp() {
        client = new OpenAiClientImpl(openAiProperties, restTemplate);
    }

    private ChatCompletionResponse buildResponse(String content) {
        return new ChatCompletionResponse(List.of(new Choice(new ChatMessage("assistant", content))));
    }

    // 1. Blank API key → throws OpenAiUnavailableException with MISSING_API_KEY, no HTTP call made
    @Test
    void complete_blankApiKey_throwsMissingApiKey_noHttpCall() {
        when(openAiProperties.getApiKey()).thenReturn("   ");

        assertThatThrownBy(() -> client.complete("test prompt"))
                .isInstanceOf(OpenAiUnavailableException.class)
                .satisfies(ex -> assertThat(((OpenAiUnavailableException) ex).getFallbackReason())
                        .isEqualTo(FallbackReason.MISSING_API_KEY));

        verifyNoInteractions(restTemplate);
    }

    // 2. Null API key → throws OpenAiUnavailableException with MISSING_API_KEY
    @Test
    void complete_nullApiKey_throwsMissingApiKey() {
        when(openAiProperties.getApiKey()).thenReturn(null);

        assertThatThrownBy(() -> client.complete("test prompt"))
                .isInstanceOf(OpenAiUnavailableException.class)
                .satisfies(ex -> assertThat(((OpenAiUnavailableException) ex).getFallbackReason())
                        .isEqualTo(FallbackReason.MISSING_API_KEY));

        verifyNoInteractions(restTemplate);
    }

    // 3. Valid API key → calls restTemplate.postForObject with correct URL (baseUrl from properties)
    @Test
    void complete_validApiKey_callsCorrectUrl() {
        when(openAiProperties.getApiKey()).thenReturn("test-key");
        when(openAiProperties.getBaseUrl()).thenReturn("https://api.groq.com/openai/v1/chat/completions");
        when(openAiProperties.getModel()).thenReturn("llama-3.1-8b-instant");
        when(openAiProperties.getTemperature()).thenReturn(0.7);
        when(openAiProperties.getMaxTokens()).thenReturn(100);
        when(restTemplate.postForObject(anyString(), any(), eq(ChatCompletionResponse.class)))
                .thenReturn(buildResponse("result"));

        client.complete("prompt");

        verify(restTemplate).postForObject(
                eq("https://api.groq.com/openai/v1/chat/completions"),
                any(),
                eq(ChatCompletionResponse.class)
        );
    }

    // 4. Authorization header is exactly "Bearer <apiKey>"
    @Test
    void complete_authorizationHeaderIsExactlyBearerApiKey() {
        when(openAiProperties.getApiKey()).thenReturn("my-secret-key");
        when(openAiProperties.getBaseUrl()).thenReturn("https://api.example.com");
        when(openAiProperties.getModel()).thenReturn("gpt-4");
        when(openAiProperties.getTemperature()).thenReturn(0.7);
        when(openAiProperties.getMaxTokens()).thenReturn(100);
        when(restTemplate.postForObject(anyString(), any(), eq(ChatCompletionResponse.class)))
                .thenReturn(buildResponse("result"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<ChatCompletionRequest>> captor = ArgumentCaptor.forClass(HttpEntity.class);

        client.complete("prompt");

        verify(restTemplate).postForObject(anyString(), captor.capture(), eq(ChatCompletionResponse.class));
        String authHeader = captor.getValue().getHeaders().getFirst("Authorization");
        assertThat(authHeader).isEqualTo("Bearer my-secret-key");
    }

    // 5. Request body contains configured model, temperature, max_tokens
    @Test
    void complete_requestBodyContainsConfiguredModelTemperatureMaxTokens() {
        when(openAiProperties.getApiKey()).thenReturn("key");
        when(openAiProperties.getBaseUrl()).thenReturn("https://api.example.com");
        when(openAiProperties.getModel()).thenReturn("llama-3.1-8b-instant");
        when(openAiProperties.getTemperature()).thenReturn(0.7);
        when(openAiProperties.getMaxTokens()).thenReturn(100);
        when(restTemplate.postForObject(anyString(), any(), eq(ChatCompletionResponse.class)))
                .thenReturn(buildResponse("result"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<ChatCompletionRequest>> captor = ArgumentCaptor.forClass(HttpEntity.class);

        client.complete("prompt");

        verify(restTemplate).postForObject(anyString(), captor.capture(), eq(ChatCompletionResponse.class));
        ChatCompletionRequest body = captor.getValue().getBody();
        assertThat(body).isNotNull();
        assertThat(body.model()).isEqualTo("llama-3.1-8b-instant");
        assertThat(body.temperature()).isEqualTo(0.7);
        assertThat(body.maxTokens()).isEqualTo(100);
    }

    // 6. choices[0].message.content is extracted and returned
    @Test
    void complete_extractsChoicesFirstMessageContent() {
        when(openAiProperties.getApiKey()).thenReturn("key");
        when(openAiProperties.getBaseUrl()).thenReturn("https://api.example.com");
        when(openAiProperties.getModel()).thenReturn("model");
        when(openAiProperties.getTemperature()).thenReturn(0.7);
        when(openAiProperties.getMaxTokens()).thenReturn(100);
        when(restTemplate.postForObject(anyString(), any(), eq(ChatCompletionResponse.class)))
                .thenReturn(buildResponse("Send welcome email"));

        String result = client.complete("prompt");

        assertThat(result).isEqualTo("Send welcome email");
    }

    // 7. ResourceAccessException on first call → retries once (restTemplate called twice total), returns result on second call
    @Test
    void complete_resourceAccessExceptionOnFirstCall_retriesOnce_returnsOnSecondCall() {
        when(openAiProperties.getApiKey()).thenReturn("key");
        when(openAiProperties.getBaseUrl()).thenReturn("https://api.example.com");
        when(openAiProperties.getModel()).thenReturn("model");
        when(openAiProperties.getTemperature()).thenReturn(0.7);
        when(openAiProperties.getMaxTokens()).thenReturn(100);
        when(restTemplate.postForObject(anyString(), any(), eq(ChatCompletionResponse.class)))
                .thenThrow(new ResourceAccessException("timeout"))
                .thenReturn(buildResponse("retry result"));

        String result = client.complete("prompt");

        assertThat(result).isEqualTo("retry result");
        verify(restTemplate, times(2)).postForObject(anyString(), any(), eq(ChatCompletionResponse.class));
    }

    // 8. ResourceAccessException on both calls → propagates the exception
    @Test
    void complete_resourceAccessExceptionOnBothCalls_propagatesException() {
        when(openAiProperties.getApiKey()).thenReturn("key");
        when(openAiProperties.getBaseUrl()).thenReturn("https://api.example.com");
        when(openAiProperties.getModel()).thenReturn("model");
        when(openAiProperties.getTemperature()).thenReturn(0.7);
        when(openAiProperties.getMaxTokens()).thenReturn(100);
        when(restTemplate.postForObject(anyString(), any(), eq(ChatCompletionResponse.class)))
                .thenThrow(new ResourceAccessException("timeout"));

        assertThatThrownBy(() -> client.complete("prompt"))
                .isInstanceOf(ResourceAccessException.class);

        verify(restTemplate, times(2)).postForObject(anyString(), any(), eq(ChatCompletionResponse.class));
    }
}
