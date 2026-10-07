package com.mphotle.threatbrief.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.mphotle.threatbrief.exception.InvalidDateRangeException;
import com.mphotle.threatbrief.model.ThreatBrief;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Core orchestration service responsible for coordinating threat briefing retrieval, cache management, 
 * and date range validation.
 * <p>
 * Integrates {@link NvdService} and {@link LlmService} to synthesize intelligence reports within a strict 14-day 
 * rolling window, supporting rate-throttled background pre-generation.
 * </p>
 */
@Slf4j
@Service
public class ThreatBriefService {

    private final NvdService nvdService;
    private final LlmService llmService;
    private final Cache<LocalDate, ThreatBrief> threatBriefCache;

    public ThreatBriefService(
        NvdService nvdService,
        LlmService llmService,
        Cache<LocalDate, ThreatBrief> threatBriefCache
    ) {
        this.nvdService = nvdService;
        this.llmService = llmService;
        this.threatBriefCache = threatBriefCache;
    }

    public Mono<ThreatBrief> getBriefForDate(LocalDate date) {
        validateDateRange(date);

        ThreatBrief cachedBrief = threatBriefCache.getIfPresent(date);
        if (cachedBrief != null) {
            return Mono.just(cachedBrief);
        }

        return nvdService.fetchVulnerabilitiesForDate(date)
                .flatMap(vulnerabilities -> llmService.generateBrief(vulnerabilities)
                        .map(content -> new ThreatBrief(
                                date,
                                content,
                                vulnerabilities.totalCount(),
                                LocalDateTime.now(ZoneOffset.UTC)
                        ))
                )
                .doOnNext(brief -> threatBriefCache.put(date, brief));
    }

    public void pregenerateBriefsForPast14Days() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Flux.range(0, 15)
                .concatMap(i -> Mono.delay(i == 0 ? Duration.ZERO : Duration.ofSeconds(6))
                        .then(Mono.defer(() -> {
                            LocalDate date = today.minusDays(i);
                            return getBriefForDate(date)
                                    .doOnNext(brief -> log.info("Pre-generated brief for {}", date))
                                    .doOnError(error -> log.warn("Failed to pre-generate brief for {}: {}", date, error.getMessage()))
                                    .onErrorResume(e -> Mono.empty());
                        }))
                )
                .subscribe();
    }

    private void validateDateRange(LocalDate date) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate oldestAllowed = today.minusDays(14);

        if (date.isBefore(oldestAllowed) || date.isAfter(today)) {
            throw new InvalidDateRangeException(
                "Threat briefings are only available for the past 14 days (" + oldestAllowed + " to " + today + ")."
            );
        }
    }
}
