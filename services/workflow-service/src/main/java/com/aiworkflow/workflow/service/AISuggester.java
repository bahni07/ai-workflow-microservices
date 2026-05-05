package com.aiworkflow.workflow.service;

import java.util.List;

public interface AISuggester {
    String suggestNextStep(String workflowName, List<String> existingStepNames);
}
