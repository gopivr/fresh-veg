package com.fresveg.account.infrastructure.config;

import com.fresveg.common.http.ServletCorrelationFilter;
import com.fresveg.common.http.CorrelationIds;
import com.fresveg.account.api.AccountProblems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class FoundationConfiguration {
    @Bean
    FilterRegistrationBean<ServletCorrelationFilter> correlationFilter() {
        var registration = new FilterRegistrationBean<>(new ServletCorrelationFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    SecurityFilterChain foundationSecurity(HttpSecurity http, JsonMapper mapper,
            @Value("${springdoc.api-docs.enabled:false}") boolean docsEnabled) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) -> unauthenticated(request, response, mapper))
                        .accessDeniedHandler((request, response, error) -> forbidden(request, response, mapper)))
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> { })
                        .authenticationEntryPoint((request, response, error) -> unauthenticated(request, response, mapper))
                        .accessDeniedHandler((request, response, error) -> forbidden(request, response, mapper)))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/liveness",
                            "/actuator/health/readiness", "/actuator/prometheus").permitAll();
                    if (docsEnabled) {
                        auth.requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs.yaml").permitAll();
                    }
                    auth.requestMatchers("/api/v1/accounts/**").authenticated().anyRequest().denyAll();
                })
                .build();
    }

    private static void unauthenticated(HttpServletRequest request, HttpServletResponse response, JsonMapper mapper)
            throws IOException {
        if (request.getRequestURI().startsWith("/api/v1/accounts/")) {
            response.setHeader("WWW-Authenticate", "Bearer");
            writeProblem(request, response, mapper, HttpStatus.UNAUTHORIZED, "SEC-401-001", "A valid bearer token is required.");
        } else {
            forbidden(request, response, mapper);
        }
    }

    private static void forbidden(HttpServletRequest request, HttpServletResponse response, JsonMapper mapper)
            throws IOException {
        writeProblem(request, response, mapper, HttpStatus.FORBIDDEN, "SEC-403-001", "Access to this resource is denied.");
    }

    private static void writeProblem(HttpServletRequest request, HttpServletResponse response, JsonMapper mapper,
            HttpStatus status, String code, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        response.getOutputStream().write(mapper.writeValueAsBytes(AccountProblems.create(status, code, detail, request)));
    }
}
