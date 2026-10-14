package com.lore.ai;

/**
 * Thrown when no AI provider is set up, or the provider could not produce a result.
 * Callers save the summary or explanation as FAILED so it can be retried later.
 * Messages must never contain the API key.
 */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
