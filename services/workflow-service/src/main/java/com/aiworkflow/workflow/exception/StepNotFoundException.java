package com.aiworkflow.workflow.exception;

import java.util.UUID;

public class StepNotFoundException extends RuntimeException {
    public StepNotFoundException(UUID id) {
        super("Step not found: " + id);
    }
}
