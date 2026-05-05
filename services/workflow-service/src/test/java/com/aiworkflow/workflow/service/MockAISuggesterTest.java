package com.aiworkflow.workflow.service;

import com.aiworkflow.workflow.service.impl.MockAISuggester;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockAISuggesterTest {

    private final MockAISuggester suggester = new MockAISuggester();

    @Test
    void suggestNextStep_noExistingSteps_returnsStep1() {
        String result = suggester.suggestNextStep("My Workflow", List.of());
        assertThat(result).isEqualTo("Suggested next step for 'My Workflow' (step 1)");
    }

    @Test
    void suggestNextStep_twoExistingSteps_returnsStep3() {
        String result = suggester.suggestNextStep("My Workflow", List.of("Step A", "Step B"));
        assertThat(result).isEqualTo("Suggested next step for 'My Workflow' (step 3)");
    }

    @Test
    void suggestNextStep_deterministic_sameInputProducesSameOutput() {
        String first = suggester.suggestNextStep("Deterministic", List.of("one"));
        String second = suggester.suggestNextStep("Deterministic", List.of("one"));
        assertThat(first).isEqualTo(second);
    }

    @Test
    void suggestNextStep_workflowNameIncludedInOutput() {
        String result = suggester.suggestNextStep("Invoice Processing", List.of());
        assertThat(result).contains("Invoice Processing");
    }
}
