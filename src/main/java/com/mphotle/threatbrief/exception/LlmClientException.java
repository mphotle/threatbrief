package com.mphotle.threatbrief.exception;

public class LlmClientException extends LlmServiceException {
    public LlmClientException(String message) {
        super(message);
    }

    public LlmClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
