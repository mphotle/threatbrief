package com.mphotle.threatbrief.exception;

public class NvdClientException extends NvdServiceException{
    public NvdClientException(String message) {
        super(message);
    }

    public NvdClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
