package com.fresveg.gateway.infrastructure.config;

import com.fresveg.common.http.CorrelationIds;
import com.fresveg.common.http.ProblemDetails;
import com.fresveg.gateway.infrastructure.security.GatewaySecurityProperties;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.security.oauth2.jwt.Jwt;

@Configuration(proxyBeanMethods = false)
public class FoundationConfiguration {
    private static final String CUSTOMER = "CUSTOMER";
    private static final String VENDOR_ADMIN = "VENDOR_ADMIN";
    private static final String VENDOR_STAFF = "VENDOR_STAFF";
    private static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    WebFilter correlationFilter() {
        return (exchange, chain) -> {
            String id = CorrelationIds.resolve(exchange.getRequest().getHeaders().getFirst(CorrelationIds.HEADER));
            exchange.getResponse().getHeaders().set(CorrelationIds.HEADER, id);
            exchange.getAttributes().put(CorrelationIds.CONTEXT_KEY, id);
            var request = exchange.getRequest().mutate().headers(headers -> headers.set(CorrelationIds.HEADER, id)).build();
            return chain.filter(exchange.mutate().request(request).build())
                    .contextWrite(context -> context.put(CorrelationIds.CONTEXT_KEY, id));
        };
    }

    @Bean
    CorsConfigurationSource gatewayCorsConfigurationSource(GatewaySecurityProperties properties) {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", CorrelationIds.HEADER));
        configuration.setExposedHeaders(List.of(CorrelationIds.HEADER));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(1800L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    SecurityWebFilterChain foundationSecurity(ServerHttpSecurity http, JsonMapper mapper,
            Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .cors(cors -> { })
                .headers(headers -> headers
                        .contentTypeOptions(contentType -> { })
                        .frameOptions(frame -> frame.disable())
                        .hsts(hsts -> { }))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((exchange, error) -> unauthenticated(exchange, mapper))
                        .accessDeniedHandler((exchange, error) -> forbidden(exchange, mapper)))
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint((exchange, error) -> unauthenticated(exchange, mapper))
                        .accessDeniedHandler((exchange, error) -> forbidden(exchange, mapper)))
                .authorizeExchange(auth -> auth
                        .pathMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/liveness",
                                "/actuator/health/readiness", "/actuator/prometheus").permitAll()
                        .pathMatchers("/internal/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/catalog/products", "/api/v1/catalog/products/*",
                                "/api/v1/catalog/categories", "/api/v1/catalog/categories/*/products",
                                "/api/v1/supply/products/*/offers", "/api/v1/supply/listings/*",
                                "/api/v1/supply/listings/*/availability", "/api/v1/fulfillment/slots").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/catalog/**").hasRole(PLATFORM_ADMIN)
                        .pathMatchers(HttpMethod.PUT, "/api/v1/catalog/**").hasRole(PLATFORM_ADMIN)
                        .pathMatchers(HttpMethod.PATCH, "/api/v1/catalog/**").hasRole(PLATFORM_ADMIN)
                        .pathMatchers(HttpMethod.DELETE, "/api/v1/catalog/**").hasRole(PLATFORM_ADMIN)
                        .pathMatchers("/api/v1/supply/vendor/**").hasAnyRole(VENDOR_ADMIN, VENDOR_STAFF)
                        .pathMatchers(HttpMethod.POST, "/api/v1/supply/**").hasRole(VENDOR_ADMIN)
                        .pathMatchers(HttpMethod.PUT, "/api/v1/supply/**").hasRole(VENDOR_ADMIN)
                        .pathMatchers(HttpMethod.PATCH, "/api/v1/supply/**").hasRole(VENDOR_ADMIN)
                        .pathMatchers(HttpMethod.DELETE, "/api/v1/supply/**").hasRole(VENDOR_ADMIN)
                        .pathMatchers("/api/v1/vendor/orders/**", "/api/v1/vendor/orders").hasAnyRole(VENDOR_ADMIN, VENDOR_STAFF)
                        .pathMatchers("/api/v1/carts/**", "/api/v1/carts", "/api/v1/checkout/preview",
                                "/api/v1/orders/**", "/api/v1/orders", "/api/v1/fulfillments/**").hasRole(CUSTOMER)
                        .pathMatchers("/api/v1/accounts/**").hasAnyRole(CUSTOMER, VENDOR_ADMIN, VENDOR_STAFF, PLATFORM_ADMIN)
                        .anyExchange().denyAll())
                .build();
    }

    private static Mono<Void> unauthenticated(ServerWebExchange exchange, JsonMapper mapper) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (requiresBearer(path)) {
            exchange.getResponse().getHeaders().set("WWW-Authenticate", "Bearer");
            return writeProblem(exchange, mapper, HttpStatus.UNAUTHORIZED, "SEC-401-001", "Unauthorized", "A valid bearer token is required.");
        }
        return forbidden(exchange, mapper);
    }

    private static boolean requiresBearer(String path) {
        return path.startsWith("/api/v1/accounts/")
                || path.equals("/api/v1/accounts/me")
                || path.startsWith("/api/v1/catalog/")
                || path.startsWith("/api/v1/supply/")
                || path.startsWith("/api/v1/carts")
                || path.equals("/api/v1/checkout/preview")
                || path.startsWith("/api/v1/orders")
                || path.startsWith("/api/v1/vendor/orders")
                || path.startsWith("/api/v1/fulfillments/");
    }

    private static Mono<Void> forbidden(ServerWebExchange exchange, JsonMapper mapper) {
        return writeProblem(exchange, mapper, HttpStatus.FORBIDDEN, "SEC-403-001", "Forbidden", "Access to this resource is denied.");
    }

    private static Mono<Void> writeProblem(ServerWebExchange exchange, JsonMapper mapper, HttpStatus status,
            String code, String title, String detail) {
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        var problem = status == HttpStatus.FORBIDDEN
                ? ProblemDetails.forbidden(exchange.getAttribute(CorrelationIds.CONTEXT_KEY))
                : org.springframework.http.ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        problem.setProperty(CorrelationIds.CONTEXT_KEY, exchange.getAttribute(CorrelationIds.CONTEXT_KEY));
        byte[] body = mapper.writeValueAsBytes(problem);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
}
