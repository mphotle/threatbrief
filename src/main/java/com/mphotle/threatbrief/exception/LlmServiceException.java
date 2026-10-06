package com.mphotle.threatbrief.exception;

public class LlmServiceException extends DownstreamServiceException {
    public LlmServiceException(String message) {
        super(message);
    }

    public LlmServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
