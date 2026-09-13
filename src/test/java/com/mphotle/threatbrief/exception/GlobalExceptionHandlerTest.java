package com.mphotle.threatbrief.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler globalExceptionHandler;

    @BeforeEach
    void setUp() {
        globalExceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    void handleNvdRateLimitExceededException_when_rate_limited_returns_too_many_requests_429() {
        NvdRateLimitExceededException exception = new NvdRateLimitExceededException("NVD API rate limit exceeded.");

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleNvdRateLimitExceededException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(429);
        assertThat(body.error()).isEqualTo("Too Many Requests");
        assertThat(body.message()).isEqualTo("Upstream threat intelligence rate limit exceeded. Please try again shortly.");
        assertThat(body.timestamp()).isNotNull();
        assertThat(body.timestamp()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    void handleNvdClientExceptions_when_client_error_returns_bad_request_400() {
        NvdClientException exception = new NvdClientException("NVD API client error: Status 400 BAD_REQUEST");

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleNvdClientExceptions(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(400);
        assertThat(body.error()).isEqualTo("Bad Request");
        assertThat(body.message()).isEqualTo("Invalid request parameter or date format for vulnerability lookup.");
        assertThat(body.timestamp()).isNotNull();
        assertThat(body.timestamp()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    void handleNvdServerException_when_server_error_returns_bad_gateway_502() {
        NvdServerException exception = new NvdServerException("NVD API server error. Downstream unavailable.");

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleNvdServerException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(502);
        assertThat(body.error()).isEqualTo("Bad Gateway");
        assertThat(body.message()).isEqualTo("The downstream vulnerability service is currently experiencing outages. Please try again later.");
        assertThat(body.timestamp()).isNotNull();
        assertThat(body.timestamp()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    void handleNvdServiceException_when_communication_failure_returns_bad_gateway_502() {
        NvdServiceException exception = new NvdServiceException("Network failure connecting to NVD API: Connection reset");

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleNvdServiceException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(502);
        assertThat(body.error()).isEqualTo("Bad Gateway");
        assertThat(body.message()).isEqualTo("Unable to connect to the downstream vulnerability provider.");
        assertThat(body.timestamp()).isNotNull();
        assertThat(body.timestamp()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    void handleDownstreamServiceException_when_generic_downstream_error_returns_internal_server_error_500() {
        DownstreamServiceException exception = new DownstreamServiceException("Unknown downstream integration failure");

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleDownstreamServiceException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(500);
        assertThat(body.error()).isEqualTo("Downstream Service Error");
        assertThat(body.message()).isEqualTo("The system encountered an error communicating with an external dependency.");
        assertThat(body.timestamp()).isNotNull();
        assertThat(body.timestamp()).isBeforeOrEqualTo(LocalDateTime.now());
    }
}
