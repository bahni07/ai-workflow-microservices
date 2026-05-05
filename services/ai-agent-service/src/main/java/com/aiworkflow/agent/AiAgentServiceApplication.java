package com.aiworkflow.agent;

import com.aiworkflow.agent.config.OpenAiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Main entry point for the AI Agent Service (Phase 4).
 *
 * <p>Startup validation of openai.base-url and openai.model is performed via
 * {@code @PostConstruct} in {@code OpenAiProperties} (see task 2.1).
 */
@SpringBootApplication
@EnableConfigurationProperties(OpenAiProperties.class)
public class AiAgentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiAgentServiceApplication.class, args);
    }
}
