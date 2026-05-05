package com.aiworkflow.agent.kafka.property;

import com.aiworkflow.agent.dto.SuggestionRequest;
import com.aiworkflow.agent.dto.SuggestionResponse;
import com.aiworkflow.agent.kafka.WorkflowCreatedEvent;
import com.aiworkflow.agent.kafka.WorkflowEventConsumer;
import com.aiworkflow.agent.model.SuggestionSource;
import com.aiworkflow.agent.service.AiSuggestionService;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowEventConsumerPropertyTest {

    @Property(tries = 100)
    void property3_consumerIdempotency(
            @ForAll("workflowIds") UUID workflowId,
            @ForAll @NotBlank @StringLength(max = 255) String workflowName,
            @ForAll("callCounts") int callCount) {

        AiSuggestionService mockService = Mockito.mock(AiSuggestionService.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> mockTemplate = Mockito.mock(KafkaTemplate.class);

        when(mockService.suggest(any()))
                .thenReturn(new SuggestionResponse("suggestion", SuggestionSource.AI));

        WorkflowEventConsumer consumer = new WorkflowEventConsumer(mockService, mockTemplate);
        WorkflowCreatedEvent event = new WorkflowCreatedEvent(workflowId, workflowName);

        for (int i = 0; i < callCount; i++) {
            consumer.onWorkflowCreated(event);
        }

        ArgumentCaptor<SuggestionRequest> captor = ArgumentCaptor.forClass(SuggestionRequest.class);
        verify(mockService, times(callCount)).suggest(captor.capture());

        captor.getAllValues().forEach(req ->
                assertThat(req.workflowName()).isEqualTo(workflowName));
    }

    @Property(tries = 100)
    void property4_consumerExceptionIsolation(
            @ForAll("workflowIds") UUID workflowId,
            @ForAll @NotBlank @StringLength(max = 255) String workflowName) {

        AiSuggestionService mockService = Mockito.mock(AiSuggestionService.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> mockTemplate = Mockito.mock(KafkaTemplate.class);

        when(mockService.suggest(any())).thenThrow(new RuntimeException("simulated failure"));

        WorkflowEventConsumer consumer = new WorkflowEventConsumer(mockService, mockTemplate);
        WorkflowCreatedEvent event = new WorkflowCreatedEvent(workflowId, workflowName);

        assertThatCode(() -> consumer.onWorkflowCreated(event)).doesNotThrowAnyException();
    }

    @Provide
    Arbitrary<UUID> workflowIds() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<Integer> callCounts() {
        return Arbitraries.integers().between(1, 5);
    }
}
