package com.mphotle.threatbrief.exception;

public class LlmParseException extends LlmServiceException {
    public LlmParseException(String message) {
        super(message);
    }

    public LlmParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
