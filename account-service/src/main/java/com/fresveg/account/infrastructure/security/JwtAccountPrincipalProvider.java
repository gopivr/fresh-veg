package com.fresveg.account.infrastructure.security;

import com.fresveg.account.application.AccountPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class JwtAccountPrincipalProvider implements com.fresveg.account.application.AccountPrincipalProvider {
    @Override
    public AccountPrincipal current() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token) || !token.isAuthenticated()) {
            throw new AccessDeniedException("A validated JWT principal is required");
        }
        var jwt = token.getToken();
        String email = Boolean.TRUE.equals(jwt.getClaim("email_verified")) ? bounded(jwt.getClaim("email"), 320) : null;
        return new AccountPrincipal(jwt.getIssuer().toString(), jwt.getSubject(), bounded(jwt.getClaim("name"), 160), email);
    }

    private static String bounded(Object claim, int maximumLength) {
        return claim instanceof String value && !value.isBlank() && value.length() <= maximumLength ? value.strip() : null;
    }
}
