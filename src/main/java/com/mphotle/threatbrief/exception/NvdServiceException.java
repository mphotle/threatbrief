package com.mphotle.threatbrief.exception;

public class NvdServiceException extends DownstreamServiceException {
    public NvdServiceException(String message) {
        super(message);
    }

    public NvdServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
