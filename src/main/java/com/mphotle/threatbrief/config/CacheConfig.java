package com.mphotle.threatbrief.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mphotle.threatbrief.model.DailyVulnerabilities;
import com.mphotle.threatbrief.model.ThreatBrief;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;

/**
 * Spring configuration class responsible for initializing in-memory Caffeine caches.
 * <p>
 * Manages high-performance cache instances for daily NVD vulnerability datasets
 * and synthesized LLM threat briefings to reduce external API requests and lower latency.
 * </p>
 */
@Configuration
public class CacheConfig {

    @Bean
    public static Cache<LocalDate, DailyVulnerabilities> dailyVulnerabilitiesCache() {
        return Caffeine.newBuilder()
                .maximumSize(100)
                .recordStats()
                .build();
    }

    @Bean
    public static Cache<LocalDate, ThreatBrief> threatBriefCache() {
        return Caffeine.newBuilder()
                .maximumSize(30)
                .recordStats()
                .build();
    }
}