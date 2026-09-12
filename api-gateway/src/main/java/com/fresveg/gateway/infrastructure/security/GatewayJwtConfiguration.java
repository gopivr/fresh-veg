package com.fresveg.gateway.infrastructure.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewaySecurityProperties.class)
public class GatewayJwtConfiguration {
    @Bean
    ReactiveJwtDecoder gatewayJwtDecoder(GatewaySecurityProperties properties) {
        var decoder = NimbusReactiveJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
        OAuth2TokenValidator<Jwt> requiredClaims = jwt -> {
            String subject = jwt.getSubject();
            boolean valid = jwt.getExpiresAt() != null
                    && subject != null
                    && !subject.isBlank()
                    && subject.length() <= 255
                    && jwt.getAudience() != null
                    && jwt.getAudience().contains(properties.audience());
            return valid ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Required token claims are invalid", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuerUri()), requiredClaims));
        return decoder;
    }

    @Bean
    Converter<Jwt, Mono<AbstractAuthenticationToken>> gatewayJwtAuthenticationConverter() {
        return jwt -> Mono.just(new JwtAuthenticationToken(jwt, authorities(jwt), jwt.getSubject()));
    }

    private static Collection<GrantedAuthority> authorities(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        addRoles(authorities, jwt.getClaimAsStringList("roles"));
        addRoles(authorities, jwt.getClaimAsStringList("authorities"));
        Object realmAccess = jwt.getClaims().get("realm_access");
        if (realmAccess instanceof java.util.Map<?, ?> map && map.get("roles") instanceof List<?> roles) {
            addRoles(authorities, roles.stream().filter(String.class::isInstance).map(String.class::cast).toList());
        }
        return authorities;
    }

    private static void addRoles(Set<GrantedAuthority> authorities, List<String> roles) {
        if (roles == null) {
            return;
        }
        for (String role : roles) {
            if (role == null || role.isBlank()) {
                continue;
            }
            String normalized = role.startsWith("ROLE_") ? role : "ROLE_" + role.toUpperCase(Locale.ROOT);
            authorities.add(new SimpleGrantedAuthority(normalized));
        }
    }
}
