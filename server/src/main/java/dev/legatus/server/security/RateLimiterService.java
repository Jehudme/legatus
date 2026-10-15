package dev.legatus.server.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

/**
 * Token-bucket in-memory rate limiter per IP address for public quarantine endpoints.
 * <p>
 * Complies with {@code ADR-0005} and {@code server-spec.md} Section 4.1:
 * Enforces a strict limit of 5 requests per 60 seconds per IP address.
 */
@Service
public class RateLimiterService {

    public static final int DEFAULT_MAX_REQUESTS_PER_MINUTE = 5;
    private static final Duration WINDOW_DURATION = Duration.ofMinutes(1);

    private final int maxRequestsPerWindow;
    private final Cache<String, TokenBucket> ipBuckets;

    public RateLimiterService() {
        this(DEFAULT_MAX_REQUESTS_PER_MINUTE);
    }

    public RateLimiterService(int maxRequestsPerWindow) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.ipBuckets = Caffeine.newBuilder()
            .expireAfterAccess(WINDOW_DURATION.multipliedBy(2))
            .maximumSize(100_000)
            .build();
    }

    /**
     * Checks if a request from the given IP address is permitted under rate limit policy.
     *
     * @param ipAddress client IP address
     * @return {@code true} if allowed; {@code false} if rate limit exceeded
     */
    public boolean tryAcquire(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return false;
        }

        TokenBucket bucket = ipBuckets.get(ipAddress, key -> new TokenBucket(maxRequestsPerWindow));
        return bucket.tryConsume();
    }

    private static class TokenBucket {
        private final int capacity;
        private final AtomicInteger tokens;
        private volatile Instant windowStart;

        TokenBucket(int capacity) {
            this.capacity = capacity;
            this.tokens = new AtomicInteger(capacity);
            this.windowStart = Instant.now();
        }

        synchronized boolean tryConsume() {
            Instant now = Instant.now();
            if (Duration.between(windowStart, now).compareTo(WINDOW_DURATION) >= 0) {
                // Reset window
                tokens.set(capacity);
                windowStart = now;
            }

            if (tokens.get() > 0) {
                tokens.decrementAndGet();
                return true;
            }
            return false;
        }
    }
}
