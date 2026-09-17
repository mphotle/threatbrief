package com.mphotle.threatbrief.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.github.benmanes.caffeine.cache.Cache;
import com.mphotle.threatbrief.exception.NvdClientException;
import com.mphotle.threatbrief.exception.NvdRateLimitExceededException;
import com.mphotle.threatbrief.exception.NvdServerException;
import com.mphotle.threatbrief.exception.NvdServiceException;
import com.mphotle.threatbrief.model.DailyVulnerabilities;
import com.mphotle.threatbrief.parser.NvdResponseParser;

import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class NvdService {

    private final WebClient nvdWebClient;
    private final NvdResponseParser nvdResponseParser;
    private final Cache<LocalDate, DailyVulnerabilities> cache;

    public NvdService(
        WebClient nvdWebClient,
        NvdResponseParser nvdResponseParser,
        Cache<LocalDate, DailyVulnerabilities> cache
    ) {
        this.nvdWebClient = nvdWebClient;
        this.nvdResponseParser = nvdResponseParser;
        this.cache = cache;
    }

    public Mono<DailyVulnerabilities> fetchVulnerabilitiesForDate(LocalDate date) {
        DailyVulnerabilities cachedVulnerabilities = cache.getIfPresent(date);
        if (cachedVulnerabilities != null) {
            return Mono.just(cachedVulnerabilities);
        }

        String startDateTime = date.atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + ".000Z";
        String endDateTime = date.atTime(23, 59, 59).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + ".000Z";

        ResponseSpec responseSpec = nvdWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("pubStartDate", startDateTime)
                        .queryParam("pubEndDate", endDateTime)
                        .build())
                .retrieve();

        Mono<DailyVulnerabilities> dailyVulnerabilities = applyErrorStatusHandlers(responseSpec)
                .bodyToMono(String.class)
                .transform(this::applyResilience)
                .map(rawJson -> nvdResponseParser.parse(rawJson, date));

        return dailyVulnerabilities.doOnNext(vulnerabilities -> cache.put(date, vulnerabilities));
    }

    private ResponseSpec applyErrorStatusHandlers(ResponseSpec responseSpec) {
        return responseSpec
                .onStatus(status -> status.isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS), response -> 
                    Mono.error(new NvdRateLimitExceededException("NVD API rate limit exceeded."))
                )
                .onStatus(status -> status.is4xxClientError(), response ->
                    response.createException().flatMap(exception ->
                        Mono.error(new NvdClientException("NVD API client error: Status " + response.statusCode(), exception))
                    )
                )
                .onStatus(status -> status.is5xxServerError(), response -> 
                    response.createException().flatMap(exception ->
                        Mono.error(new NvdServerException("NVD API server error. Downstream unavailable. Status " + response.statusCode(), exception))
                    )
                );
    }

    private <T> Mono<T> applyResilience(Mono<T> mono) {
        return mono
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                    .filter(throwable -> throwable instanceof NvdServerException)
                    .maxBackoff(Duration.ofSeconds(10))
                )
                .onErrorMap(WebClientResponseException.class, exception -> 
                    new NvdServiceException("Network failure connecting to NVD API: " + exception.getMessage(), exception)
                );
    }

}
