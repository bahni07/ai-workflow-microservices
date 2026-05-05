package com.aiworkflow.agent.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openai")
public class OpenAiProperties {

    private static final Logger log = LoggerFactory.getLogger(OpenAiProperties.class);

    private String apiKey;
    private String baseUrl;
    private String model;
    private double temperature;
    private int maxTokens;
    private int connectTimeoutMs;
    private int readTimeoutMs;

    @PostConstruct
    public void validateConfig() {
        if (baseUrl == null || baseUrl.isBlank()) {
            log.warn("OpenAI configuration warning: 'openai.base-url' is blank — AI provider calls will fail");
        }
        if (model == null || model.isBlank()) {
            log.warn("OpenAI configuration warning: 'openai.model' is blank — AI provider calls will fail");
        }
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}
