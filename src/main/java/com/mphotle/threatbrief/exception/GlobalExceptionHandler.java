package com.mphotle.threatbrief.exception;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;

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

    @ExceptionHandler(InvalidDateRangeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDateRangeException(InvalidDateRangeException exception) {
        log.warn("Invalid date range requested: {}", exception.getMessage());
        return buildResponse(
            HttpStatus.BAD_REQUEST,
            "Bad Request",
            exception.getMessage()
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

    @ExceptionHandler(LlmRateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleLlmRateLimitExceededException(LlmRateLimitExceededException exception) {
        log.warn("LLM provider rate limit exceeded: {}", exception.getMessage());
        return buildResponse(
            HttpStatus.TOO_MANY_REQUESTS,
            "Too Many Requests",
            "Upstream LLM provider rate limit exceeded. Please try again shortly."
        );
    }

    @ExceptionHandler(LlmClientException.class)
    public ResponseEntity<ErrorResponse> handleLlmClientException(LlmClientException exception) {
        log.error("LLM provider client error: {}", exception.getMessage(), exception);
        return buildResponse(
            HttpStatus.BAD_GATEWAY,
            "Bad Gateway",
            "Configuration or request error with the downstream LLM provider."
        );
    }

    @ExceptionHandler(LlmServerException.class)
    public ResponseEntity<ErrorResponse> handleLlmServerException(LlmServerException exception) {
        log.error("LLM provider server error downstream: {}", exception.getMessage(), exception);
        return buildResponse(
            HttpStatus.BAD_GATEWAY,
            "Bad Gateway",
            "The downstream LLM provider is currently experiencing issues. Please try again later."
        );
    }

    @ExceptionHandler(LlmParseException.class)
    public ResponseEntity<ErrorResponse> handleLlmParseException(LlmParseException exception) {
        log.error("Failed to parse LLM response payload: {}", exception.getMessage(), exception);
        return buildResponse(
            HttpStatus.BAD_GATEWAY,
            "Bad Gateway",
            "Failed to parse downstream LLM response payload."
        );
    }

    @ExceptionHandler(LlmServiceException.class)
    public ResponseEntity<ErrorResponse> handleLlmServiceException(LlmServiceException exception) {
        log.error("LLM provider communication failure: {}", exception.getMessage(), exception);
        return buildResponse(
            HttpStatus.BAD_GATEWAY,
            "Bad Gateway",
            "Unable to communicate with the downstream LLM provider."
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

    @ExceptionHandler(NvdParseException.class)
    public ResponseEntity<ErrorResponse> handleNvdParseException(NvdParseException exception) {
        log.error("Failed to parse NVD payload: {}", exception.getMessage(), exception);
        return buildResponse(
                HttpStatus.BAD_GATEWAY,
                "Bad Gateway",
                "Failed to parse upstream vulnerability data payload."
        );
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ErrorResponse> handleServerWebInputException(ServerWebInputException exception) {
        log.warn("Malformed HTTP request input: {}", exception.getReason());
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                "Invalid query parameter format or missing required field."
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception exception) {
        log.error("Unhandled internal server error: {}", exception.getMessage(), exception);
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected internal error occurred."
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