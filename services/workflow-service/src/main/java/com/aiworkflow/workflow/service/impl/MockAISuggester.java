package com.aiworkflow.workflow.service.impl;

import com.aiworkflow.workflow.service.AISuggester;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MockAISuggester implements AISuggester {

    @Override
    public String suggestNextStep(String workflowName, List<String> existingStepNames) {
        int nextStepNumber = existingStepNames.size() + 1;
        return "Suggested next step for '" + workflowName + "' (step " + nextStepNumber + ")";
    }
}
