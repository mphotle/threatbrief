package com.mphotle.threatbrief.exception;

public class NvdRateLimitExceededException extends NvdClientException {
    public NvdRateLimitExceededException(String message) {
        super(message);
    }
}
