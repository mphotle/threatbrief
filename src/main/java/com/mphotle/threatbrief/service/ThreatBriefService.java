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
 * Integrates {@link NvdService} and {@link AgentService} to synthesize intelligence reports within a strict 14-day 
 * rolling window, supporting rate-throttled background pre-generation. Callers may bypass the cache lookup via the 
 * {@code forceRefresh} flag, in which case a fresh brief is generated and the cache entry is overwritten.
 * </p>
 */
@Slf4j
@Service
public class ThreatBriefService {

    private final NvdService nvdService;
    private final AgentService agentService;
    private final Cache<LocalDate, ThreatBrief> threatBriefCache;

    public ThreatBriefService(
        NvdService nvdService,
        AgentService agentService,
        Cache<LocalDate, ThreatBrief> threatBriefCache
    ) {
        this.nvdService = nvdService;
        this.agentService = agentService;
        this.threatBriefCache = threatBriefCache;
    }

    public Mono<ThreatBrief> getBriefForDate(LocalDate date) {
        return getBriefForDate(date, false);
    }

    public Mono<ThreatBrief> getBriefForDate(LocalDate date, boolean forceRefresh) {
        validateDateRange(date);

        if (!forceRefresh) {
            ThreatBrief cachedBrief = threatBriefCache.getIfPresent(date);
            if (cachedBrief != null) {
                return Mono.just(cachedBrief);
            }
        }

        return nvdService.fetchVulnerabilitiesForDate(date, forceRefresh)
                .flatMap(vulnerabilities -> agentService.generateBrief(vulnerabilities)
                        .map(content -> new ThreatBrief(
                                date,
                                content,
                                vulnerabilities.totalCount(),
                                LocalDateTime.now(ZoneOffset.UTC)
                        ))
                )
                .doOnNext(brief -> {
                    if (brief.totalCount() > 0) {
                        threatBriefCache.put(date, brief);
                    }
                });
    }

    public void pregenerateBriefsForPast14Days() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Flux.range(0, 15)
                .concatMap(i -> Mono.delay(i == 0 ? Duration.ZERO : Duration.ofSeconds(6))
                        .then(Mono.defer(() -> {
                            LocalDate date = today.minusDays(i);
                            // Refresh cache for current day to include newly published vulnerabilities for the day
                            return getBriefForDate(date, i == 0)
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
