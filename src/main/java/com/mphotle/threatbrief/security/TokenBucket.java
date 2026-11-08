package com.mphotle.threatbrief.security;

import java.time.Duration;

/**
 * Thread-safe token bucket implementation used for in-memory rate limiting and throttling.
 * <p>
 * Tokens refill continuously over time based on elapsed nanoseconds up to a configured maximum capacity.
 * Provides nanosecond-precision calculation of wait times for HTTP {@code Retry-After} response headers.
 * </p>
 */
public class TokenBucket {

    private final long capacity;
    private final long refillTokens;
    private final long refillDurationNanos;
    private double availableTokens;
    private long lastRefillTimeNanos;

    public TokenBucket(long capacity, long refillTokens, Duration refillDuration) {
        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.refillDurationNanos = refillDuration.toNanos();
        this.availableTokens = capacity;
        this.lastRefillTimeNanos = System.nanoTime();
    }

    /**
     * Attempts to consume a single token.
     *
     * @return {@code true} if a token was available and consumed; {@code false} if exhausted
     */
    public synchronized boolean tryConsume() {
        refill();
        if (availableTokens >= 1.0) {
            availableTokens -= 1.0;
            return true;
        }
        return false;
    }

    /**
     * Calculates the estimated wait time in seconds until at least one token becomes available.
     *
     * @return remaining seconds until next token refill, minimum 1 second if empty
     */
    public synchronized long getSecondsUntilNextToken() {
        refill();
        if (availableTokens >= 1.0) {
            return 0L;
        }
        double needed = 1.0 - availableTokens;
        double refillRatePerNano = (double) refillTokens / (double) refillDurationNanos;
        long nanosNeeded = (long) Math.ceil(needed / refillRatePerNano);
        long seconds = Duration.ofNanos(nanosNeeded).toSeconds();
        return Math.max(1L, seconds);
    }

    private void refill() {
        long now = System.nanoTime();
        long elapsedNanos = now - lastRefillTimeNanos;
        if (elapsedNanos > 0) {
            double refillRatePerNano = (double) refillTokens / (double) refillDurationNanos;
            double tokensToAdd = elapsedNanos * refillRatePerNano;
            this.availableTokens = Math.min((double) capacity, this.availableTokens + tokensToAdd);
            this.lastRefillTimeNanos = now;
        }
    }
}
