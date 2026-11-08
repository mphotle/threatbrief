package com.mphotle.threatbrief.security;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TokenBucketTest {

    @Test
    void tryConsume_when_tokens_available_returns_true() {
        TokenBucket bucket = new TokenBucket(3, 3, Duration.ofSeconds(60));

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();
    }

    @Test
    void getSecondsUntilNextToken_when_exhausted_returns_positive_delay() {
        TokenBucket bucket = new TokenBucket(1, 1, Duration.ofSeconds(60));

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();

        long waitSeconds = bucket.getSecondsUntilNextToken();
        assertThat(waitSeconds).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void getSecondsUntilNextToken_when_tokens_remain_returns_zero() {
        TokenBucket bucket = new TokenBucket(5, 5, Duration.ofSeconds(60));

        assertThat(bucket.getSecondsUntilNextToken()).isEqualTo(0L);
    }
}
