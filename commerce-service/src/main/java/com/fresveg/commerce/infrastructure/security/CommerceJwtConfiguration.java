package com.fresveg.commerce.infrastructure.security;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.client.RestTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CommerceSecurityProperties.class)
public class CommerceJwtConfiguration {
    @Bean
    JwtDecoder commerceJwtDecoder(CommerceSecurityProperties properties) {
        var requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Duration.ofSeconds(5));
        requests.setReadTimeout(Duration.ofSeconds(5));
        var decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256).restOperations(new RestTemplate(requests)).build();
        OAuth2TokenValidator<Jwt> requiredClaims = jwt -> {
            String subject = jwt.getSubject();
            boolean valid = jwt.getExpiresAt() != null && subject != null && !subject.isBlank() && subject.length() <= 255
                    && jwt.getAudience() != null && jwt.getAudience().contains(properties.audience());
            return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Required token claims are invalid", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuerUri()), requiredClaims));
        return decoder;
    }
}
