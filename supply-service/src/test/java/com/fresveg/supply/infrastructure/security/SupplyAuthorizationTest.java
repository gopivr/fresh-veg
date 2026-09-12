package com.fresveg.supply.infrastructure.security;

import static org.assertj.core.api.Assertions.*;
import com.fresveg.supply.application.SupplyException;
import com.fresveg.supply.infrastructure.client.SupplyDependencies;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.json.JsonMapper;

class SupplyAuthorizationTest {
    private HttpServer server;
    private SupplyDependencies dependencies;
    private SupplyAuthorization authorization;
    private final UUID user=UUID.randomUUID(),vendor=UUID.randomUUID(),cursor=UUID.randomUUID();
    private int status=200,pages;
    private boolean malformed,loop,staff;
    private String forwarded;
    @BeforeEach
    void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/accounts/me",exchange -> {
            forwarded=exchange.getRequestHeaders().getFirst("Authorization");
            String body;
            if (malformed) { body="not-json"; }
            else if (!exchange.getRequestURI().getPath().endsWith("vendor-memberships")) { body="{\"data\":{\"userId\":\""+user+"\",\"status\":\"ACTIVE\"}}"; }
            else {
                pages++;
                if (loop || !exchange.getRequestURI().getQuery().contains("cursor=")) {
                    body="{\"data\":[],\"pagination\":{\"hasNext\":true,\"nextCursor\":\""+cursor+"\"}}";
                } else {
                    body="{\"data\":[{\"vendorId\":\""+vendor+"\",\"role\":\""+(staff?"VENDOR_STAFF":"VENDOR_ADMIN")+"\"}],\"pagination\":{\"hasNext\":false}}";
                }
            }
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,bytes.length);
            try (var out=exchange.getResponseBody()) { out.write(bytes); }
        });server.start();
        String base="http://127.0.0.1:"+server.getAddress().getPort();dependencies=new SupplyDependencies(base,base,new JsonMapper());authorization=new SupplyAuthorization(dependencies);
        var jwt=Jwt.withTokenValue("test-boundary-token").header("alg","RS256").subject("subject").claim("roles",List.of("PLATFORM_ADMIN")).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,List.of()));
    }
    @AfterEach
    void stop() { SecurityContextHolder.clearContext();dependencies.destroy();server.stop(0); }
    @Test
    void membershipTraversalFindsLaterPagesAndForwardsTheActualToken() {
        assertThat(authorization.requireVendor(vendor,true)).isEqualTo(user);assertThat(pages).isEqualTo(2);
        assertThat(forwarded).isEqualTo("Bearer test-boundary-token");
        staff=true;assertThatThrownBy(() -> authorization.requireVendor(vendor,true)).isInstanceOf(AccessDeniedException.class);
        assertThat(authorization.requireVendor(vendor,false)).isEqualTo(user);
    }
    @Test
    void malformedUnavailableAndLoopingResponsesFailClosed() {
        status=503;assertThatThrownBy(() -> authorization.requireVendor(vendor,true)).isInstanceOf(SupplyException.class);
        status=200;malformed=true;assertThatThrownBy(() -> authorization.requireVendor(vendor,true)).isInstanceOf(SupplyException.class);
        malformed=false;loop=true;assertThatThrownBy(() -> authorization.requireVendor(vendor,true)).isInstanceOf(SupplyException.class);
        loop=false;status=403;assertThatThrownBy(() -> authorization.requireVendor(vendor,true)).isInstanceOf(AccessDeniedException.class);
    }
}
