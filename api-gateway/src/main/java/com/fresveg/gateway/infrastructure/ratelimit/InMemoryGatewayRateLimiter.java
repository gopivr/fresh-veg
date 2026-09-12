package com.fresveg.gateway.infrastructure.ratelimit;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class InMemoryGatewayRateLimiter implements GatewayRateLimiter {
    private final Clock clock;
    private final int requestsPerMinute;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Autowired
    public InMemoryGatewayRateLimiter(@Value("${gateway.rate-limit.requests-per-minute:600}") int requestsPerMinute) {
        this(Clock.systemUTC(), requestsPerMinute);
    }

    public InMemoryGatewayRateLimiter(Clock clock, int requestsPerMinute) {
        this.clock = clock;
        this.requestsPerMinute = Math.max(1, requestsPerMinute);
    }

    @Override
    public Mono<Boolean> allow(ServerWebExchange exchange) {
        String key = exchange.getRequest().getRemoteAddress() == null
                ? "unknown"
                : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        long minute = clock.instant().getEpochSecond() / 60;
        Bucket bucket = buckets.compute(key, (ignored, existing) -> {
            if (existing == null || existing.minute != minute) {
                return new Bucket(minute, new AtomicInteger(0));
            }
            return existing;
        });
        return Mono.just(bucket.count.incrementAndGet() <= requestsPerMinute);
    }

    private record Bucket(long minute, AtomicInteger count) {
    }
}
