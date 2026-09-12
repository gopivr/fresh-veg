package com.fresveg.gateway.infrastructure.config;

import com.fresveg.common.http.CorrelationIds;
import com.fresveg.common.http.ProblemDetails;
import com.fresveg.gateway.infrastructure.ratelimit.GatewayRateLimiter;
import java.net.URI;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class GatewayRouteConfiguration {
    private static final Logger log = LoggerFactory.getLogger(GatewayRouteConfiguration.class);

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    WebFilter responseSecurityHeadersFilter() {
        return (exchange, chain) -> {
            var headers = exchange.getResponse().getHeaders();
            headers.set("X-Content-Type-Options", "nosniff");
            headers.set("X-Frame-Options", "DENY");
            headers.set("Referrer-Policy", "no-referrer");
            headers.set("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
            return chain.filter(exchange);
        };
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 2)
    WebFilter internalRouteBlocker(JsonMapper mapper) {
        return (exchange, chain) -> {
            if (exchange.getRequest().getPath().pathWithinApplication().value().startsWith("/internal/")) {
                return writeProblem(exchange, mapper, HttpStatus.FORBIDDEN);
            }
            return chain.filter(exchange);
        };
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 3)
    WebFilter rateLimitFilter(GatewayRateLimiter rateLimiter, JsonMapper mapper) {
        return (exchange, chain) -> rateLimiter.allow(exchange).flatMap(allowed -> {
            if (!allowed) {
                return writeProblem(exchange, mapper, HttpStatus.TOO_MANY_REQUESTS);
            }
            return chain.filter(exchange);
        });
    }

    @Bean
    GlobalFilter requestHeaderPropagationFilter() {
        return (exchange, chain) -> {
            String correlationId = exchange.getAttribute(CorrelationIds.CONTEXT_KEY);
            var builder = exchange.getRequest().mutate();
            if (correlationId != null) {
                builder.header(CorrelationIds.HEADER, correlationId);
            }
            String authorization = exchange.getRequest().getHeaders().getFirst("Authorization");
            if (authorization != null && !authorization.isBlank()) {
                builder.header("Authorization", authorization);
            }
            return chain.filter(exchange.mutate().request(builder.build()).build());
        };
    }

    @Bean
    GlobalFilter routeLoggingFilter() {
        return (exchange, chain) -> {
            long start = System.nanoTime();
            String method = exchange.getRequest().getMethod().name();
            String path = exchange.getRequest().getPath().pathWithinApplication().value();
            String correlationId = exchange.getAttribute(CorrelationIds.CONTEXT_KEY);
            return chain.filter(exchange).doFinally(signal -> {
                var status = exchange.getResponse().getStatusCode();
                long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
                log.info("gateway route completed method={} path={} status={} durationMs={} correlationId={}",
                        method, path, status == null ? 0 : status.value(), elapsedMs, correlationId);
            });
        };
    }

    private static ProblemDetail tooManyRequests(String correlationId) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, "Request rate limit exceeded.");
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Too Many Requests");
        problem.setProperty("code", "SEC-429-001");
        problem.setProperty(CorrelationIds.CONTEXT_KEY, correlationId);
        return problem;
    }

    private static Mono<Void> writeProblem(ServerWebExchange exchange, JsonMapper mapper, HttpStatus status) {
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        byte[] body = mapper.writeValueAsBytes(status == HttpStatus.TOO_MANY_REQUESTS
                ? tooManyRequests(exchange.getAttribute(CorrelationIds.CONTEXT_KEY))
                : ProblemDetails.forbidden(exchange.getAttribute(CorrelationIds.CONTEXT_KEY)));
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
}
