package com.aiworkflow.agent.kafka;

import com.aiworkflow.agent.dto.SuggestionRequest;
import com.aiworkflow.agent.dto.SuggestionResponse;
import com.aiworkflow.agent.service.AiSuggestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class WorkflowEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEventConsumer.class);
    static final String TOPIC_AI_SUGGESTION = "ai.suggestion.generated";

    private final AiSuggestionService aiSuggestionService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public WorkflowEventConsumer(AiSuggestionService aiSuggestionService,
                                  KafkaTemplate<String, Object> kafkaTemplate) {
        this.aiSuggestionService = aiSuggestionService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "workflow.created", groupId = "ai-agent-service")
    public void onWorkflowCreated(WorkflowCreatedEvent event) {
        try {
            SuggestionRequest request = new SuggestionRequest(event.workflowName(), Collections.emptyList());
            SuggestionResponse response = aiSuggestionService.suggest(request);
            AiSuggestionGeneratedEvent outEvent =
                    new AiSuggestionGeneratedEvent(event.workflowId(), response.suggestion());
            kafkaTemplate.send(TOPIC_AI_SUGGESTION, event.workflowId().toString(), outEvent);
        } catch (Exception e) {
            log.error("consumer.processing.failed topic=workflow.created workflowId={}", event.workflowId(), e);
        }
    }
}
