package com.mphotle.threatbrief.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mphotle.threatbrief.config.CacheConfig;
import com.mphotle.threatbrief.exception.InvalidDateRangeException;
import com.mphotle.threatbrief.model.DailyVulnerabilities;
import com.mphotle.threatbrief.model.ThreatBrief;
import com.mphotle.threatbrief.model.VulnerabilityItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ThreatBriefServiceTest {

    @Mock
    private NvdService nvdService;

    @Mock
    private LlmService llmService;

    private Cache<LocalDate, ThreatBrief> threatBriefCache;
    private ThreatBriefService threatBriefService;

    @BeforeEach
    void setUp() {
        this.threatBriefCache = CacheConfig.threatBriefCache();
        this.threatBriefService = new ThreatBriefService(nvdService, llmService, threatBriefCache);
    }

    @Test
    void getBriefForDate_when_date_within_14_days_fetches_generates_caches_and_returns() {
        LocalDate validDate = LocalDate.now(ZoneOffset.UTC).minusDays(5);
        DailyVulnerabilities mockVulnerabilities = new DailyVulnerabilities(
                validDate,
                1,
                List.of(new VulnerabilityItem("CVE-2026-0001", "CRITICAL", 9.8, "Critical bug"))
        );
        String mockMarkdown = "# Executive Briefing";

        when(nvdService.fetchVulnerabilitiesForDate(validDate)).thenReturn(Mono.just(mockVulnerabilities));
        when(llmService.generateBrief(mockVulnerabilities)).thenReturn(Mono.just(mockMarkdown));

        ThreatBrief result = threatBriefService.getBriefForDate(validDate).block();

        assertThat(result).isNotNull();
        assertThat(result.date()).isEqualTo(validDate);
        assertThat(result.content()).isEqualTo(mockMarkdown);
        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(threatBriefCache.getIfPresent(validDate)).isNotNull();
    }

    @Test
    void getBriefForDate_when_cached_returns_without_calling_nvd_or_llm() {
        LocalDate validDate = LocalDate.now(ZoneOffset.UTC).minusDays(2);
        ThreatBrief existingBrief = new ThreatBrief(validDate, "# Cached Brief", 3, null);
        threatBriefCache.put(validDate, existingBrief);

        ThreatBrief result = threatBriefService.getBriefForDate(validDate).block();

        assertThat(result).isEqualTo(existingBrief);
        verify(nvdService, never()).fetchVulnerabilitiesForDate(any());
        verify(llmService, never()).generateBrief(any());
    }

    @Test
    void getBriefForDate_when_date_older_than_14_days_throws_InvalidDateRangeException() {
        LocalDate oldDate = LocalDate.now(ZoneOffset.UTC).minusDays(15);

        assertThatThrownBy(() -> threatBriefService.getBriefForDate(oldDate))
                .isInstanceOf(InvalidDateRangeException.class)
                .hasMessageContaining("Threat briefings are only available for the past 14 days");
    }

    @Test
    void getBriefForDate_when_date_in_future_throws_InvalidDateRangeException() {
        LocalDate futureDate = LocalDate.now(ZoneOffset.UTC).plusDays(1);

        assertThatThrownBy(() -> threatBriefService.getBriefForDate(futureDate))
                .isInstanceOf(InvalidDateRangeException.class)
                .hasMessageContaining("Threat briefings are only available for the past 14 days");
    }
}
