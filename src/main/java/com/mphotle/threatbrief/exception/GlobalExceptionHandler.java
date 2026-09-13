package com.mphotle.threatbrief.exception;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@Slf4j 
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NvdRateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleNvdRateLimitExceededException(NvdRateLimitExceededException exception) {
        log.warn("NVD rate limit exceeded: {}", exception.getMessage());
        return buildResponse(
            HttpStatus.TOO_MANY_REQUESTS,
            "Too Many Requests",
            "Upstream threat intelligence rate limit exceeded. Please try again shortly."
        );
    }

    @ExceptionHandler(NvdClientException.class)
    public ResponseEntity<ErrorResponse> handleNvdClientExceptions(NvdClientException exception) {
        log.warn("NVD API client exception: {}", exception.getMessage());
        return buildResponse(
            HttpStatus.BAD_REQUEST,
            "Bad Request",
            "Invalid request parameter or date format for vulnerability lookup."
        );
    }

    @ExceptionHandler(NvdServerException.class)
    public ResponseEntity<ErrorResponse> handleNvdServerException(NvdServerException exception) {
        log.error("NVD API server error downstream (retries exhausted): {}", exception.getMessage(), exception);
        return buildResponse(
            HttpStatus.BAD_GATEWAY,
            "Bad Gateway",
            "The downstream vulnerability service is currently experiencing outages. Please try again later."
        );
    }

    @ExceptionHandler(NvdServiceException.class)
    public ResponseEntity<ErrorResponse> handleNvdServiceException(NvdServiceException exception) {
        log.error("NVD communication failure: {}", exception.getMessage(), exception);
        return buildResponse(
            HttpStatus.BAD_GATEWAY,
            "Bad Gateway",
            "Unable to connect to the downstream vulnerability provider."
        );
    }

    @ExceptionHandler(DownstreamServiceException.class)
    public ResponseEntity<ErrorResponse> handleDownstreamServiceException(DownstreamServiceException exception) {
        log.error("Unhandled downstream service exception: {}", exception.getMessage(), exception);
        return buildResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Downstream Service Error",
            "The system encountered an error communicating with an external dependency."
        );
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String error, String message) {
        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                error,
                message
        );
        return ResponseEntity.status(status).body(response);
    }
}