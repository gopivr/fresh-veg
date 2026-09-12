package com.fresveg.gateway.infrastructure.ratelimit;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public interface GatewayRateLimiter {
    Mono<Boolean> allow(ServerWebExchange exchange);
}
