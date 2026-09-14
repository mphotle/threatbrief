package com.mphotle.threatbrief.exception;

public class NvdServerException extends NvdServiceException{
    public NvdServerException(String message) {
        super(message);
    }

    public NvdServerException(String message, Throwable cause) {
        super(message, cause);
    }
}
