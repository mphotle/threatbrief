package com.mphotle.threatbrief.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Service managing client-specific token buckets for endpoint throttling.
 * <p>
 * Maintains distinct Caffeine caches for chat and briefing operations with automatic eviction of inactive
 * client IPs to prevent memory leaks in constrained environments.
 * </p>
 */
@Service
public class RateLimiterService {

    private final boolean enabled;
    private final long chatCapacity;
    private final long chatRefillTokens;
    private final Duration chatRefillDuration;

    private final long briefCapacity;
    private final long briefRefillTokens;
    private final Duration briefRefillDuration;

    private final Cache<String, TokenBucket> chatBuckets;
    private final Cache<String, TokenBucket> briefBuckets;

    public RateLimiterService(
            @Value("${threatbrief.rate-limit.enabled:true}") boolean enabled,
            @Value("${threatbrief.rate-limit.chat.capacity:10}") long chatCapacity,
            @Value("${threatbrief.rate-limit.chat.refill-tokens:10}") long chatRefillTokens,
            @Value("${threatbrief.rate-limit.chat.refill-duration-seconds:60}") long chatRefillDurationSeconds,
            @Value("${threatbrief.rate-limit.brief.capacity:30}") long briefCapacity,
            @Value("${threatbrief.rate-limit.brief.refill-tokens:30}") long briefRefillTokens,
            @Value("${threatbrief.rate-limit.brief.refill-duration-seconds:60}") long briefRefillDurationSeconds
    ) {
        this.enabled = enabled;
        this.chatCapacity = chatCapacity;
        this.chatRefillTokens = chatRefillTokens;
        this.chatRefillDuration = Duration.ofSeconds(chatRefillDurationSeconds);

        this.briefCapacity = briefCapacity;
        this.briefRefillTokens = briefRefillTokens;
        this.briefRefillDuration = Duration.ofSeconds(briefRefillDurationSeconds);

        this.chatBuckets = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(10))
                .maximumSize(10_000)
                .build();

        this.briefBuckets = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(10))
                .maximumSize(10_000)
                .build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public ConsumptionResult tryConsumeChat(String clientIp) {
        if (!enabled) {
            return ConsumptionResult.ALLOWED;
        }
        TokenBucket bucket = chatBuckets.get(clientIp, k ->
                new TokenBucket(chatCapacity, chatRefillTokens, chatRefillDuration));
        if (bucket.tryConsume()) {
            return ConsumptionResult.ALLOWED;
        }
        long retryAfter = bucket.getSecondsUntilNextToken();
        return ConsumptionResult.rejected(retryAfter);
    }

    public ConsumptionResult tryConsumeBrief(String clientIp) {
        if (!enabled) {
            return ConsumptionResult.ALLOWED;
        }
        TokenBucket bucket = briefBuckets.get(clientIp, k ->
                new TokenBucket(briefCapacity, briefRefillTokens, briefRefillDuration));
        if (bucket.tryConsume()) {
            return ConsumptionResult.ALLOWED;
        }
        long retryAfter = bucket.getSecondsUntilNextToken();
        return ConsumptionResult.rejected(retryAfter);
    }

    public record ConsumptionResult(boolean allowed, long retryAfterSeconds) {
        public static final ConsumptionResult ALLOWED = new ConsumptionResult(true, 0);

        public static ConsumptionResult rejected(long retryAfterSeconds) {
            return new ConsumptionResult(false, retryAfterSeconds);
        }
    }
}
