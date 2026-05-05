package com.aiworkflow.workflow.event;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;

class StubEventPublisherTest {

    private final StubEventPublisher publisher = new StubEventPublisher();

    @Test
    void publishWorkflowCreated_doesNotThrow() {
        assertThatCode(() -> publisher.publishWorkflowCreated(UUID.randomUUID(), "My Workflow"))
                .doesNotThrowAnyException();
    }

    @Test
    void publishStepCreated_doesNotThrow() {
        assertThatCode(() -> publisher.publishStepCreated(UUID.randomUUID(), UUID.randomUUID(), "Step One"))
                .doesNotThrowAnyException();
    }

    @Test
    void publishStepCompleted_doesNotThrow() {
        assertThatCode(() -> publisher.publishStepCompleted(UUID.randomUUID(), UUID.randomUUID()))
                .doesNotThrowAnyException();
    }
}
