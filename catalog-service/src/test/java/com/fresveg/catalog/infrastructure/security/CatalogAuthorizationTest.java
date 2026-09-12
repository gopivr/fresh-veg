package com.fresveg.catalog.infrastructure.security;

import static org.assertj.core.api.Assertions.*;
import com.fresveg.catalog.application.CatalogException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.json.JsonMapper;

class CatalogAuthorizationTest {
    private HttpServer server;
    private CatalogAuthorization authorization;
    private int status=500;
    private String body="{}";
    @BeforeEach
    void setup() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/accounts/me",exchange -> {
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status,bytes.length);
            try (var out=exchange.getResponseBody()) { out.write(bytes); }
        });server.start();
        authorization=new CatalogAuthorization("http://127.0.0.1:"+server.getAddress().getPort(),new JsonMapper());
        var jwt=Jwt.withTokenValue("test-adapter-boundary").header("alg","RS256").subject("user")
                .claim("roles",List.of("PLATFORM_ADMIN")).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,List.of()));
    }
    @AfterEach
    void stop() { SecurityContextHolder.clearContext(); authorization.destroy();server.stop(0); }
    @Test
    void unavailableOrMalformedAccountResponsesFailClosed() {
        assertThatThrownBy(authorization::requireAdmin).isInstanceOf(CatalogException.class)
                .satisfies(e -> assertThat(((CatalogException)e).status().value()).isEqualTo(503));
        status=200;body="not-json";
        assertThatThrownBy(authorization::requireAdmin).isInstanceOf(CatalogException.class);
        body="{\"data\":{\"userId\":\"not-uuid\",\"roles\":[\"PLATFORM_ADMIN\"],\"status\":\"ACTIVE\"}}";
        assertThatThrownBy(authorization::requireAdmin).isInstanceOf(CatalogException.class);
    }
    @Test
    void accountDenialAndInactiveIdentityCannotBeOverriddenByTokenRoles() {
        status=403; assertThatThrownBy(authorization::requireAdmin).isInstanceOf(AccessDeniedException.class);
        status=200;body="{\"data\":{\"userId\":\"40000000-0000-0000-0000-000000000001\",\"roles\":[\"PLATFORM_ADMIN\"],\"status\":\"SUSPENDED\"}}";
        assertThatThrownBy(authorization::requireAdmin).isInstanceOf(AccessDeniedException.class);
    }
}
