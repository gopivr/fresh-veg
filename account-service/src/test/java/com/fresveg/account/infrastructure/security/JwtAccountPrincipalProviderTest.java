package com.fresveg.account.infrastructure.security;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class JwtAccountPrincipalProviderTest {
    private final JwtAccountPrincipalProvider provider = new JwtAccountPrincipalProvider();

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void refusesMissingNonJwtAndUnauthenticatedPrincipals() {
        assertThatThrownBy(provider::current).isInstanceOf(AccessDeniedException.class);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("user", "unused", List.of()));
        assertThatThrownBy(provider::current).isInstanceOf(AccessDeniedException.class);
        var token = new JwtAuthenticationToken(jwt().build(), List.of());
        token.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(token);
        assertThatThrownBy(provider::current).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void optionalProfileClaimsMustHaveExpectedTypesAndBounds() {
        authenticate(jwt().claim("name", "n".repeat(161)).claim("email", "person@example.test")
                .claim("email_verified", "true").build());
        var principal = provider.current();
        assertThat(principal.displayName()).isNull();
        assertThat(principal.verifiedEmail()).isNull();
        authenticate(jwt().claim("name", List.of("not a string")).claim("email", "x".repeat(321))
                .claim("email_verified", true).build());
        assertThat(provider.current().displayName()).isNull();
        assertThat(provider.current().verifiedEmail()).isNull();
    }

    @Test
    void keepsOpaqueIdentityExactAndNormalizesOnlyProfileText() {
        authenticate(jwt().claim("name", "  Customer  ").claim("email", " person@example.test ")
                .claim("email_verified", true).build());
        var principal = provider.current();
        assertThat(principal.issuer()).isEqualTo("https://issuer.example.test");
        assertThat(principal.subject()).isEqualTo("opaque subject");
        assertThat(principal.displayName()).isEqualTo("Customer");
        assertThat(principal.verifiedEmail()).isEqualTo("person@example.test");
    }

    private static Jwt.Builder jwt() {
        return Jwt.withTokenValue("test-principal-only").header("alg", "RS256")
                .issuer("https://issuer.example.test").subject("opaque subject");
    }
    private static void authenticate(Jwt jwt) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }
}
