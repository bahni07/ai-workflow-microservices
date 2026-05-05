package com.aiworkflow.agent.exception;

import com.aiworkflow.agent.model.FallbackReason;

public class OpenAiUnavailableException extends RuntimeException {

    private final FallbackReason fallbackReason;

    public OpenAiUnavailableException(FallbackReason reason) {
        super("OpenAI unavailable: " + reason.name());
        this.fallbackReason = reason;
    }

    public FallbackReason getFallbackReason() {
        return fallbackReason;
    }
}
