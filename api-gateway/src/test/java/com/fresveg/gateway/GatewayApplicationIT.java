package com.fresveg.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.fresveg.common.http.CorrelationIds;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayApplicationIT {
    private static final Backend BACKEND = Backend.start();

    @LocalServerPort
    int port;
    @Autowired
    JsonMapper mapper;
    @Autowired
    Environment environment;

    @DynamicPropertySource
    static void gatewayRoutes(DynamicPropertyRegistry registry) {
        String baseUrl = "http://127.0.0.1:" + BACKEND.port();
        registry.add("ACCOUNT_BASE_URL", () -> baseUrl);
        registry.add("CATALOG_BASE_URL", () -> baseUrl);
        registry.add("SUPPLY_BASE_URL", () -> baseUrl);
        registry.add("COMMERCE_BASE_URL", () -> baseUrl);
        registry.add("FULFILLMENT_BASE_URL", () -> baseUrl);
    }

    @AfterAll
    static void stopBackend() {
        BACKEND.stop();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"})
    void probesAreHealthyAndDoNotDiscloseComponents(String path) throws Exception {
        var response = get(path, "phase13-probe");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(response.body()).get("status").asString()).isEqualTo("UP");
        assertThat(mapper.readTree(response.body()).has("components")).isFalse();
        assertThat(response.headers().firstValue(CorrelationIds.HEADER)).contains("phase13-probe");
        assertThat(response.headers().firstValue("set-cookie")).isEmpty();
    }

    @Test
    void missingAndUnsafeCorrelationIdsAreReplaced() throws Exception {
        for (String supplied : new String[] {null, "unsafe/id", "a".repeat(129)}) {
            var response = get("/actuator/health", supplied);
            String id = response.headers().firstValue(CorrelationIds.HEADER).orElseThrow();
            assertThat(UUID.fromString(id).toString()).isEqualTo(id);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/internal/v1/test", "/actuator/env", "/login"})
    void blockedRoutesAreDeniedWithoutLoginRedirect(String path) throws Exception {
        var response = get(path, "denied-request");
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("content-type")).contains("application/problem+json");
        var problem = mapper.readTree(response.body());
        assertThat(problem.get("status").asInt()).isEqualTo(403);
        assertThat(problem.get("title").asString()).isEqualTo("Forbidden");
        assertThat(problem.get("correlationId").asString()).isEqualTo("denied-request");
        assertThat(response.headers().firstValue("location")).isEmpty();
        assertThat(response.headers().firstValue("www-authenticate")).isEmpty();
        assertThat(response.headers().firstValue(CorrelationIds.HEADER)).contains("denied-request");
        assertSecurityHeaders(response);
    }

    @Test
    void protectedPublicApiRequiresJwt() throws Exception {
        var response = get("/api/v1/accounts/me", "needs-auth");
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("www-authenticate")).contains("Bearer");
        var problem = mapper.readTree(response.body());
        assertThat(problem.get("code").asString()).isEqualTo("SEC-401-001");
        assertThat(problem.get("correlationId").asString()).isEqualTo("needs-auth");
    }

    @Test
    void publicRoutesProxyWithoutAuthenticationAndApplyCorsAndHeaders() throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base() + "/api/v1/catalog/products"))
                .timeout(Duration.ofSeconds(10))
                .header(CorrelationIds.HEADER, "public-route")
                .header("Origin", "http://client.example.test")
                .GET()
                .build();
        var response = send(request);
        assertThat(response.statusCode()).isEqualTo(200);
        var body = mapper.readTree(response.body());
        assertThat(body.get("path").asString()).isEqualTo("/api/v1/catalog/products");
        assertThat(body.get("correlationId").asString()).isEqualTo("public-route");
        assertThat(body.get("authorization").isNull()).isTrue();
        assertThat(response.headers().firstValue("access-control-allow-origin")).contains("http://client.example.test");
        assertSecurityHeaders(response);
    }

    @Test
    void customerRoutesRequireCustomerRoleAndPropagateAuthorization() throws Exception {
        var forbidden = getWithBearer("/api/v1/carts", "vendor", "cart-route");
        assertThat(forbidden.statusCode()).isEqualTo(403);

        var accepted = getWithBearer("/api/v1/carts", "customer", "cart-route");
        assertThat(accepted.statusCode()).isEqualTo(200);
        var body = mapper.readTree(accepted.body());
        assertThat(body.get("path").asString()).isEqualTo("/api/v1/carts");
        assertThat(body.get("correlationId").asString()).isEqualTo("cart-route");
        assertThat(body.get("authorization").asString()).isEqualTo("Bearer customer");
    }

    @Test
    void vendorAndPlatformRoutesUseCoarseRolesBeforeForwarding() throws Exception {
        assertThat(getWithBearer("/api/v1/vendor/orders", "customer", "vendor-route").statusCode()).isEqualTo(403);
        assertThat(getWithBearer("/api/v1/vendor/orders", "vendor", "vendor-route").statusCode()).isEqualTo(200);

        var catalogWrite = HttpRequest.newBuilder(URI.create(base() + "/api/v1/catalog/products"))
                .timeout(Duration.ofSeconds(10))
                .header(CorrelationIds.HEADER, "admin-route")
                .header("Authorization", "Bearer admin")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        assertThat(send(catalogWrite).statusCode()).isEqualTo(200);

        var deniedCatalogWrite = HttpRequest.newBuilder(URI.create(base() + "/api/v1/catalog/products"))
                .timeout(Duration.ofSeconds(10))
                .header(CorrelationIds.HEADER, "admin-route")
                .header("Authorization", "Bearer customer")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        assertThat(send(deniedCatalogWrite).statusCode()).isEqualTo(403);
    }

    @Test
    void corsPreflightIsAnsweredAtGateway() throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base() + "/api/v1/carts"))
                .timeout(Duration.ofSeconds(10))
                .header("Origin", "http://client.example.test")
                .header("Access-Control-Request-Method", "POST")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        var response = send(request);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("access-control-allow-origin")).contains("http://client.example.test");
        assertThat(response.headers().firstValue("access-control-allow-methods")).hasValueSatisfying(methods -> assertThat(methods).contains("POST"));
    }

    @Test
    void serializationPreservesCamelCaseIsoTimeAndDecimalPrecision() {
        var value = new SerializationProbe("request-1", Instant.parse("2026-09-07T12:00:00Z"),
                new BigDecimal("1234567890.123456789"));
        String json = mapper.writeValueAsString(value);
        assertThat(json).contains("\"requestId\":\"request-1\"", "\"occurredAt\":\"2026-09-07T12:00:00Z\"",
                "\"amount\":1234567890.123456789");
        assertThat(mapper.readValue(json, SerializationProbe.class)).isEqualTo(value);
    }

    @Test
    void applicationUsesItsOwnConfiguration() {
        assertThat(environment.getProperty("spring.application.name")).isEqualTo("api-gateway");
    }

    private HttpResponse<String> get(String path, String correlationId) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base() + path)).timeout(Duration.ofSeconds(10));
        if (correlationId != null) {
            request.header(CorrelationIds.HEADER, correlationId);
        }
        return send(request.GET().build());
    }

    private HttpResponse<String> getWithBearer(String path, String token, String correlationId) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base() + path))
                .timeout(Duration.ofSeconds(10))
                .header(CorrelationIds.HEADER, correlationId)
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        return send(request);
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private String base() {
        return "http://127.0.0.1:" + port;
    }

    private static void assertSecurityHeaders(HttpResponse<String> response) {
        assertThat(response.headers().firstValue("x-content-type-options")).contains("nosniff");
        assertThat(response.headers().firstValue("x-frame-options")).contains("DENY");
        assertThat(response.headers().firstValue("referrer-policy")).contains("no-referrer");
        assertThat(response.headers().firstValue("permissions-policy")).contains("geolocation=(), microphone=(), camera=()");
    }

    record SerializationProbe(String requestId, Instant occurredAt, BigDecimal amount) {
    }

    @TestConfiguration
    static class TestJwtConfiguration {
        @Bean
        @Primary
        ReactiveJwtDecoder testGatewayJwtDecoder() {
            return token -> {
                List<String> roles = switch (token) {
                    case "customer" -> List.of("CUSTOMER");
                    case "vendor" -> List.of("VENDOR_STAFF");
                    case "admin" -> List.of("PLATFORM_ADMIN");
                    default -> List.of();
                };
                Jwt jwt = Jwt.withTokenValue(token)
                        .header("alg", "RS256")
                        .issuer("https://identity.example.test")
                        .audience(List.of("fresveg-gateway"))
                        .subject(token + "-subject")
                        .expiresAt(Instant.now().plusSeconds(300))
                        .claim("roles", roles)
                        .build();
                return Mono.just(jwt);
            };
        }
    }

    private record Backend(HttpServer server) {
        static Backend start() {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                server.createContext("/", exchange -> {
                    byte[] body = ("{\"path\":\"" + exchange.getRequestURI().getPath()
                            + "\",\"correlationId\":" + quote(exchange.getRequestHeaders().getFirst(CorrelationIds.HEADER))
                            + ",\"authorization\":" + quote(exchange.getRequestHeaders().getFirst("Authorization")) + "}")
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().put("Content-Type", List.of("application/json"));
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                    exchange.close();
                });
                server.setExecutor(Executors.newCachedThreadPool());
                server.start();
                return new Backend(server);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        int port() {
            return server.getAddress().getPort();
        }

        void stop() {
            server.stop(0);
        }

        private static String quote(String value) {
            return value == null ? "null" : "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
    }
}
