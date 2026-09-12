package com.fresveg.supply.infrastructure.client;

import com.fresveg.supply.application.SupplyException;
import com.fresveg.common.http.CorrelationIds;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SupplyDependencies implements DisposableBean {
    private final String account;
    private final String catalog;
    private final JsonMapper mapper;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    public SupplyDependencies(@Value("${supply.account-base-url}") String account,
            @Value("${supply.catalog-base-url}") String catalog,JsonMapper mapper) {
        this.account=base(account);this.catalog=base(catalog);this.mapper=mapper;
    }
    public JsonNode account(String path) { return get(account+path,true); }
    public JsonNode product(java.util.UUID id) { return get(catalog+"/api/v1/catalog/products/"+id,false).path("data"); }
    private JsonNode get(String url,boolean authenticated) {
        try {
            var request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5));
            if (authenticated) {
                if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token) || !token.isAuthenticated()) {
                    throw new AccessDeniedException("A validated bearer token is required");
                }
                request.header("Authorization","Bearer "+token.getToken().getTokenValue());
            }
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                Object id=attributes.getRequest().getAttribute(CorrelationIds.CONTEXT_KEY);
                if (id instanceof String value) { request.header(CorrelationIds.HEADER,value); }
            }
            var response=client.send(request.build(),HttpResponse.BodyHandlers.ofString());
            if (authenticated && (response.statusCode()==401 || response.statusCode()==403)) { throw new AccessDeniedException("Account authorization was denied"); }
            if (!authenticated && response.statusCode()==404) { throw new SupplyException(HttpStatus.NOT_FOUND,"SUP-404-001","Active catalog product not found."); }
            if (response.statusCode()!=200 || response.body().length()>1_048_576) { throw unavailable(); }
            return mapper.readTree(response.body());
        } catch (AccessDeniedException | SupplyException error) { throw error; }
        catch (InterruptedException error) { Thread.currentThread().interrupt();throw unavailable(); }
        catch (Exception error) { throw unavailable(); }
    }
    private static String base(String value) {
        URI uri=URI.create(value);
        if (!java.util.Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null) {
            throw new IllegalArgumentException("A trusted service base URL is required");
        }
        return value.replaceAll("/+$","");
    }
    public static SupplyException unavailable() { return new SupplyException(HttpStatus.SERVICE_UNAVAILABLE,"SUP-503-001","A required owner service is temporarily unavailable."); }
    @Override public void destroy() { client.close(); }
}
