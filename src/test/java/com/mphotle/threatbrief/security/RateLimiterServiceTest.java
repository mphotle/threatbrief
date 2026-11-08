package com.mphotle.threatbrief.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RateLimiterServiceTest {

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiterService = new RateLimiterService(
                true,
                2, 2, 60,
                3, 3, 60
        );
    }

    @Test
    void tryConsumeChat_allows_up_to_capacity_and_then_rejects() {
        String clientIp = "192.168.1.100";

        RateLimiterService.ConsumptionResult first = rateLimiterService.tryConsumeChat(clientIp);
        RateLimiterService.ConsumptionResult second = rateLimiterService.tryConsumeChat(clientIp);
        RateLimiterService.ConsumptionResult third = rateLimiterService.tryConsumeChat(clientIp);

        assertThat(first.allowed()).isTrue();
        assertThat(second.allowed()).isTrue();
        assertThat(third.allowed()).isFalse();
        assertThat(third.retryAfterSeconds()).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void tryConsumeBrief_allows_up_to_capacity_and_then_rejects() {
        String clientIp = "192.168.1.101";

        RateLimiterService.ConsumptionResult first = rateLimiterService.tryConsumeBrief(clientIp);
        RateLimiterService.ConsumptionResult second = rateLimiterService.tryConsumeBrief(clientIp);
        RateLimiterService.ConsumptionResult third = rateLimiterService.tryConsumeBrief(clientIp);
        RateLimiterService.ConsumptionResult fourth = rateLimiterService.tryConsumeBrief(clientIp);

        assertThat(first.allowed()).isTrue();
        assertThat(second.allowed()).isTrue();
        assertThat(third.allowed()).isTrue();
        assertThat(fourth.allowed()).isFalse();
    }

    @Test
    void different_ips_have_independent_buckets() {
        String ip1 = "10.0.0.1";
        String ip2 = "10.0.0.2";

        assertThat(rateLimiterService.tryConsumeChat(ip1).allowed()).isTrue();
        assertThat(rateLimiterService.tryConsumeChat(ip1).allowed()).isTrue();
        assertThat(rateLimiterService.tryConsumeChat(ip1).allowed()).isFalse();

        // ip2 should still have full quota
        assertThat(rateLimiterService.tryConsumeChat(ip2).allowed()).isTrue();
        assertThat(rateLimiterService.tryConsumeChat(ip2).allowed()).isTrue();
        assertThat(rateLimiterService.tryConsumeChat(ip2).allowed()).isFalse();
    }

    @Test
    void when_disabled_always_allows_consumption() {
        RateLimiterService disabledService = new RateLimiterService(
                false,
                1, 1, 60,
                1, 1, 60
        );
        String clientIp = "192.168.1.200";

        assertThat(disabledService.tryConsumeChat(clientIp).allowed()).isTrue();
        assertThat(disabledService.tryConsumeChat(clientIp).allowed()).isTrue();
        assertThat(disabledService.tryConsumeBrief(clientIp).allowed()).isTrue();
        assertThat(disabledService.tryConsumeBrief(clientIp).allowed()).isTrue();
    }
}
