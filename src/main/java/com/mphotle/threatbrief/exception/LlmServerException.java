package com.mphotle.threatbrief.exception;

public class LlmServerException extends LlmServiceException {
    public LlmServerException(String message) {
        super(message);
    }

    public LlmServerException(String message, Throwable cause) {
        super(message, cause);
    }
}
