package dev.legatus.server.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RateLimiterService}.
 */
class RateLimiterServiceTest {

    @Test
    @DisplayName("Should permit up to max requests and reject excess within the window")
    void testRateLimiterPermitsAndRejects() {
        RateLimiterService rateLimiter = new RateLimiterService(5);
        String ip = "192.168.1.50";

        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiter.tryAcquire(ip))
                .as("Request %d should be permitted", i + 1)
                .isTrue();
        }

        // 6th request from the same IP must be rejected
        assertThat(rateLimiter.tryAcquire(ip))
            .as("6th request should be rate-limited")
            .isFalse();
    }

    @Test
    @DisplayName("Should track distinct IP addresses independently")
    void testDistinctIpAddresses() {
        RateLimiterService rateLimiter = new RateLimiterService(2);

        assertThat(rateLimiter.tryAcquire("10.0.0.1")).isTrue();
        assertThat(rateLimiter.tryAcquire("10.0.0.1")).isTrue();
        assertThat(rateLimiter.tryAcquire("10.0.0.1")).isFalse();

        // Different IP address should still have full quota
        assertThat(rateLimiter.tryAcquire("10.0.0.2")).isTrue();
        assertThat(rateLimiter.tryAcquire("10.0.0.2")).isTrue();
        assertThat(rateLimiter.tryAcquire("10.0.0.2")).isFalse();
    }

    @Test
    @DisplayName("Should reject null or blank IP addresses")
    void testRejectNullOrBlankIp() {
        RateLimiterService rateLimiter = new RateLimiterService(5);
        assertThat(rateLimiter.tryAcquire(null)).isFalse();
        assertThat(rateLimiter.tryAcquire("")).isFalse();
        assertThat(rateLimiter.tryAcquire("   ")).isFalse();
    }
}
