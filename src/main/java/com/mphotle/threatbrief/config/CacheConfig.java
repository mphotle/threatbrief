package com.mphotle.threatbrief.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mphotle.threatbrief.model.DailyVulnerabilities;
import com.mphotle.threatbrief.model.ThreatBrief;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;

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