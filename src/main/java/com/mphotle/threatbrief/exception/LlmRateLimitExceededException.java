package com.mphotle.threatbrief.exception;

public class LlmRateLimitExceededException extends LlmServiceException {
    public LlmRateLimitExceededException(String message) {
        super(message);
    }

    public LlmRateLimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
