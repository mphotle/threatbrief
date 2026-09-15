package com.mphotle.threatbrief.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mphotle.threatbrief.config.JacksonConfig;
import com.mphotle.threatbrief.exception.NvdClientException;
import com.mphotle.threatbrief.exception.NvdRateLimitExceededException;
import com.mphotle.threatbrief.exception.NvdServerException;
import com.mphotle.threatbrief.exception.NvdServiceException;
import com.mphotle.threatbrief.model.DailyVulnerabilities;
import com.mphotle.threatbrief.model.VulnerabilityItem;
import com.mphotle.threatbrief.parser.NvdResponseParser;

import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class NvdServiceTest {

    private MockWebServer mockWebServer;
    private NvdService nvdService;
    private String completeMockJsonResponseBody = """
                {
                    "resultsPerPage": 1,
                    "startIndex": 0,
                    "totalResults": 1,
                    "vulnerabilities": [
                        {
                            "cve": {
                                "id": "CVE-2026-0001",
                                "vulnStatus": "Analyzed",
                                "descriptions": [
                                    {
                                        "lang": "en",
                                        "value": "Legacy vulnerability."
                                    }
                                ],
                                "metrics": {
                                    "cvssMetricV2": [
                                        {
                                            "type": "Primary",
                                            "baseSeverity": "HIGH",
                                            "cvssData": {
                                                "baseScore": 7.5
                                            }
                                        }
                                    ]
                                }
                            }
                        }
                    ]
                }
                """;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/rest/json/cves/2.0").toString())
                .build();

        ObjectMapper objectMapper = new JacksonConfig().objectMapper();
        NvdResponseParser parser = new NvdResponseParser(objectMapper);

        nvdService = new NvdService(webClient, parser);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void fetchVulnerabilitiesForDate_when_date_is_valid_returns_cve_json_payload() throws InterruptedException { 
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(completeMockJsonResponseBody));

        LocalDate targetDate = LocalDate.of(2026, 9, 8);
        DailyVulnerabilities response = nvdService.fetchVulnerabilitiesForDate(targetDate).block();

        assertThat(response.date()).isEqualTo(targetDate);
        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.items().size()).isEqualTo(1);

        for (VulnerabilityItem item : response.items()) {
            assertThat(item.id()).isEqualTo("CVE-2026-0001");
            assertThat(item.severity()).isEqualTo("HIGH");
            assertThat(item.score()).isEqualTo(7.5);
            assertThat(item.description()).isEqualTo("Legacy vulnerability.");
        }

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("GET");

        HttpUrl requestUrl = recordedRequest.getRequestUrl();
        assertThat(requestUrl).isNotNull();
        assertThat(requestUrl.queryParameter("pubStartDate")).isEqualTo("2026-09-08T00:00:00.000Z");
        assertThat(requestUrl.queryParameter("pubEndDate")).isEqualTo("2026-09-08T23:59:59.000Z");
        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }

    @Test
    void fetchVulnerabilitiesForDate_when_rate_limit_exceeded_throws_NvdRateLimitExceededException() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(429)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"message\": \"Request limit exceeded\"}"));

        LocalDate targetDate = LocalDate.of(2026, 9, 8);

        assertThatThrownBy(() -> nvdService.fetchVulnerabilitiesForDate(targetDate).block())
                .isInstanceOf(NvdRateLimitExceededException.class)
                .hasMessage("NVD API rate limit exceeded.");

        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }

    @Test
    void fetchVulnerabilitiesForDate_when_client_error_400_throws_NvdClientException() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(400)
                .setBody("Bad Request: Invalid pubStartDate"));

        LocalDate targetDate = LocalDate.of(2026, 9, 8);

        assertThatThrownBy(() -> nvdService.fetchVulnerabilitiesForDate(targetDate).block())
                .isInstanceOf(NvdClientException.class)
                .hasMessageContaining("NVD API client error: Status 400");

        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }

    @Test
    void fetchVulnerabilitiesForDate_when_client_error_404_throws_NvdClientException() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .setBody("Not Found"));

        LocalDate targetDate = LocalDate.of(2026, 9, 8);

        assertThatThrownBy(() -> nvdService.fetchVulnerabilitiesForDate(targetDate).block())
                .isInstanceOf(NvdClientException.class)
                .hasMessageContaining("NVD API client error: Status 404");

        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }

    @Test
    void fetchVulnerabilitiesForDate_when_server_error_retries_and_eventually_succeeds() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(503)
                .setBody("Service Unavailable"));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(completeMockJsonResponseBody));

        LocalDate targetDate = LocalDate.of(2026, 9, 8);
        DailyVulnerabilities response = nvdService.fetchVulnerabilitiesForDate(targetDate).block();

        assertThat(response.date()).isEqualTo(targetDate);
        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.items().size()).isEqualTo(1);
        // We do not need to assert all the response values to satisfy this test case

        assertThat(mockWebServer.getRequestCount()).isEqualTo(2);
    }

    @Test
    void applyResilience_when_server_error_retries_three_times_with_exponential_backoff() {
        AtomicInteger attempts = new AtomicInteger();
        Mono<String> sourceMono = Mono.defer(() -> {
            attempts.incrementAndGet();
            return Mono.error(new NvdServerException("NVD 500 Server Error"));
        });

        @SuppressWarnings("unchecked")
        Mono<String> resilientMono = (Mono<String>) ReflectionTestUtils
                .invokeMethod(nvdService, "applyResilience", sourceMono);

        StepVerifier.withVirtualTime(() -> resilientMono)
                .expectSubscription()
                .thenAwait(Duration.ofSeconds(20))
                .expectErrorMatches(throwable -> Exceptions.isRetryExhausted(throwable)
                        && throwable.getCause() instanceof NvdServerException)
                .verify();

        assertThat(attempts.get()).isEqualTo(4); // 1 initial + 3 retries
    }

    @Test
    void applyResilience_when_client_error_does_not_retry() {
        AtomicInteger attempts = new AtomicInteger();
        Mono<String> sourceMono = Mono.defer(() -> {
            attempts.incrementAndGet();
            return Mono.error(new NvdClientException("Client error"));
        });

        @SuppressWarnings("unchecked")
        Mono<String> resilientMono = (Mono<String>) ReflectionTestUtils
                .invokeMethod(nvdService, "applyResilience", sourceMono);

        StepVerifier.create(resilientMono)
                .expectError(NvdClientException.class)
                .verify();

        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void applyResilience_when_rate_limit_exceeded_does_not_retry() {
        AtomicInteger attempts = new AtomicInteger();
        Mono<String> sourceMono = Mono.defer(() -> {
            attempts.incrementAndGet();
            return Mono.error(new NvdRateLimitExceededException("Rate limit exceeded"));
        });

        @SuppressWarnings("unchecked")
        Mono<String> resilientMono = (Mono<String>) ReflectionTestUtils
                .invokeMethod(nvdService, "applyResilience", sourceMono);

        StepVerifier.create(resilientMono)
                .expectError(NvdRateLimitExceededException.class)
                .verify();

        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void applyResilience_when_web_client_response_exception_maps_to_NvdServiceException() {
        WebClientResponseException webClientException = WebClientResponseException.create(
                500, "Internal Server Error", HttpHeaders.EMPTY, new byte[0], null
        );

        @SuppressWarnings("unchecked")
        Mono<String> resilientMono = (Mono<String>) ReflectionTestUtils
                .invokeMethod(nvdService, "applyResilience", Mono.error(webClientException));

        StepVerifier.create(resilientMono)
                .expectErrorMatches(throwable -> throwable instanceof NvdServiceException
                        && throwable.getMessage().contains("Network failure connecting to NVD API"))
                .verify();
    }
}
