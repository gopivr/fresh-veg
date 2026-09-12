package com.fresveg.catalog.infrastructure.security;

import com.fresveg.catalog.application.CatalogException;
import com.fresveg.common.http.CorrelationIds;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.*;
import tools.jackson.databind.json.JsonMapper;

/** Account remains authoritative; signed token roles are deliberately not mapped to local authority. */
@Component("catalogAuthorization")
public class CatalogAuthorization implements DisposableBean {
    private static final String CACHE = CatalogAuthorization.class.getName();
    private final URI endpoint;
    private final JsonMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public CatalogAuthorization(@Value("${catalog.account-base-url}") String baseUrl, JsonMapper mapper) {
        URI base = URI.create(baseUrl);
        if (!java.util.Set.of("http", "https").contains(base.getScheme()) || base.getHost()==null
                || base.getUserInfo()!=null || base.getQuery()!=null || base.getFragment()!=null) {
            throw new IllegalArgumentException("A trusted Account service base URL is required");
        }
        endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/api/v1/accounts/me");
        this.mapper = mapper;
    }

    public boolean isAdmin() {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token)
                || !token.isAuthenticated()) { return false; }
        return identity(token).admin();
    }

    public UUID requireAdmin() {
        if (!isAdmin()) { throw new AccessDeniedException("Platform administrator authority is required"); }
        return identity((JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication()).userId();
    }

    private Identity identity(JwtAuthenticationToken token) {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            Object cached = servlet.getRequest().getAttribute(CACHE);
            if (cached instanceof Identity identity) { return identity; }
        }
        try {
            var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + token.getToken().getTokenValue());
            if (attributes instanceof ServletRequestAttributes servlet) {
                Object correlation = servlet.getRequest().getAttribute(CorrelationIds.CONTEXT_KEY);
                if (correlation instanceof String id) { request.header(CorrelationIds.HEADER, id); }
            }
            var response = client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode()==401 || response.statusCode()==403) {
                throw new AccessDeniedException("Account authorization was denied");
            }
            if (response.statusCode()!=200) { throw unavailable(); }
            var data = mapper.readTree(response.body()).path("data");
            if (!data.path("roles").isArray() || !data.path("status").isString()) { throw unavailable(); }
            boolean admin = false;
            for (var role : data.path("roles")) { if (role.isString() && "PLATFORM_ADMIN".equals(role.asString())) { admin=true; } }
            var identity = new Identity(UUID.fromString(data.path("userId").asString()), admin && "ACTIVE".equals(data.path("status").asString()));
            if (attributes instanceof ServletRequestAttributes servlet) { servlet.getRequest().setAttribute(CACHE, identity); }
            return identity;
        } catch (AccessDeniedException | CatalogException error) { throw error; }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw unavailable(); }
        catch (Exception error) { throw unavailable(); }
    }

    private static CatalogException unavailable() {
        return new CatalogException(HttpStatus.SERVICE_UNAVAILABLE, "CAT-503-001", "Account authorization is temporarily unavailable.");
    }
    @Override public void destroy() { client.close(); }
    private record Identity(UUID userId, boolean admin) { }
}
