package com.fresveg.account;

import static org.assertj.core.api.Assertions.assertThat;
import com.fresveg.common.http.CorrelationIds;
import com.fresveg.testing.database.ServiceDatabaseTest;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AccountApplicationIT extends ServiceDatabaseTest {
    @org.springframework.test.context.DynamicPropertySource
    static void identityProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("account.security.issuer-uri", () -> TestIdentityProvider.ISSUER);
        registry.add("account.security.audience", () -> "fresveg-account");
        registry.add("account.security.jwk-set-uri", TestIdentityProvider::jwksUri);
    }

    @LocalServerPort
    int port;
    @Autowired
    JsonMapper mapper;
    @Autowired
    Environment environment;

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"})
    void probesAreHealthyAndDoNotDiscloseComponents(String path) throws Exception {
        var response = get(path, "phase1-probe");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(response.body()).get("status").asString()).isEqualTo("UP");
        assertThat(mapper.readTree(response.body()).has("components")).isFalse();
        assertThat(response.headers().firstValue(CorrelationIds.HEADER)).contains("phase1-probe");
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
    void nonHealthRequestsAreDeniedWithoutLoginRedirect(String path) throws Exception {
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
        assertThat(environment.getProperty("spring.application.name")).isEqualTo("account-service");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(environment.getProperty("spring.sql.init.mode")).isEqualTo("never");
    }

    @Test
    void jwtValidationRejectsUntrustedOrIncompleteTokens() throws Exception {
        var subject = UUID.randomUUID().toString();
        var invalid = java.util.List.of(
                "not-a-jwt",
                TestIdentityProvider.token(subject, b -> b.issuer("https://other.example.test"), false),
                TestIdentityProvider.token(subject, b -> b.audience("other-api"), false),
                TestIdentityProvider.token(subject, b -> b.expirationTime(java.util.Date.from(Instant.now().minusSeconds(180))), false),
                TestIdentityProvider.token(subject, b -> b.expirationTime(null), false),
                TestIdentityProvider.token(subject, b -> b.subject(null), false),
                TestIdentityProvider.token(subject, b -> {}, true));
        assertProblem(call("GET", "/api/v1/accounts/me", null, null), 401);
        for (String token : invalid) {
            var response = call("GET", "/api/v1/accounts/me", token, null);
            assertProblem(response, 401);
            assertThat(response.headers().firstValue("www-authenticate")).isPresent();
        }
        try (var admin = com.fresveg.testing.database.DatabaseFixture.admin(POSTGRES)) {
            assertThat(com.fresveg.testing.database.DatabaseFixture.scalar(admin,
                    "SELECT count(*) FROM account.users WHERE oidc_subject='" + subject + "'")).isEqualTo("0");
        }
    }

    @Test
    void identityProvisioningIsStableConcurrentAndIgnoresOwnershipAndRoleClaims() throws Exception {
        String subject = UUID.randomUUID().toString();
        String token = TestIdentityProvider.token(subject);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> call("GET", "/api/v1/accounts/me", token, null));
            var second = executor.submit(() -> call("GET", "/api/v1/accounts/me", token, null));
            var a = data(first.get());
            var b = data(second.get());
            assertThat(a).isEqualTo(b);
            assertThat(a.get("roles").toString()).isEqualTo("[\"CUSTOMER\"]");
            assertThat(a.get("preferences").get("marketingOptIn").asBoolean()).isFalse();
            assertThat(a.get("email").asString()).isEqualTo("customer@example.test");
            String user = a.get("userId").asString();
            try (var admin = com.fresveg.testing.database.DatabaseFixture.admin(POSTGRES)) {
                for (String table : java.util.List.of("users", "customer_profiles", "user_preferences", "user_roles")) {
                    assertThat(com.fresveg.testing.database.DatabaseFixture.scalar(admin,
                            "SELECT count(*) FROM account." + table + " WHERE user_id='" + user + "'")).isEqualTo("1");
                }
                execute(admin, "INSERT INTO account.user_roles(user_role_id,user_id,role_id,created_by,updated_by) "
                        + "SELECT gen_random_uuid(),'" + user + "',role_id,'" + user + "','" + user + "' FROM account.roles WHERE code='PLATFORM_ADMIN'");
            }
            assertThat(data(call("GET", "/api/v1/accounts/me", token, null)).get("roles").toString())
                    .isEqualTo("[\"CUSTOMER\",\"PLATFORM_ADMIN\"]");
        }
        String unverified = TestIdentityProvider.token(UUID.randomUUID().toString(), b -> b.claim("email_verified", false), false);
        assertThat(data(call("GET", "/api/v1/accounts/me", unverified, null)).get("email").isNull()).isTrue();
    }

    @Test
    void addressesAreOwnedPaginatedValidatedAndVersioned() throws Exception {
        String alice = TestIdentityProvider.token(UUID.randomUUID().toString());
        String bob = TestIdentityProvider.token(UUID.randomUUID().toString());
        var account = data(call("GET", "/api/v1/accounts/me", alice, null));
        var created = call("POST", "/api/v1/accounts/me/addresses", alice, address(" First Street ", null));
        assertThat(created.statusCode()).isEqualTo(201);
        var address = data(created);
        String path = "/api/v1/accounts/me/addresses/" + address.get("addressId").asString();
        assertThat(created.headers().firstValue("location")).contains(path);
        assertThat(address.get("line1").asString()).isEqualTo("First Street");
        assertThat(address.get("version").asLong()).isZero();
        assertThat(address.has("customerId")).isFalse();
        assertProblem(call("PUT", path, bob, address("Foreign edit", 0L)), 404);
        assertThat(data(call("GET", "/api/v1/accounts/me/addresses", bob, null)).size()).isZero();
        assertProblem(call("PUT", "/api/v1/accounts/me/addresses/" + UUID.randomUUID(), alice, address("Missing", 0L)), 404);
        var updated = data(call("PUT", path, alice, address("Second Street", 0L)));
        assertThat(updated.get("version").asLong()).isEqualTo(1);
        assertProblem(call("PUT", path, alice, address("Stale edit", 0L)), 409);
        assertProblem(call("PUT", path, alice, address("Missing version", null)), 400);
        assertProblem(call("POST", "/api/v1/accounts/me/addresses", alice,
                address("Injected", null).replace("{", "{\"customerId\":\"" + UUID.randomUUID() + "\",")), 400);
        assertProblem(call("POST", "/api/v1/accounts/me/addresses", alice, address(" ", null)), 400);
        assertProblem(call("POST", "/api/v1/accounts/me/addresses", alice, address("Country", null).replace("US", "ZZ")), 400);
        assertProblem(call("POST", "/api/v1/accounts/me/addresses", alice, "{"), 400);
        for (String query : java.util.List.of("pageSize=0", "pageSize=101", "cursor=bad")) {
            assertProblem(call("GET", "/api/v1/accounts/me/addresses?" + query, alice, null), 400);
        }
        data(call("POST", "/api/v1/accounts/me/addresses", alice, address("Third Street", null)));
        var page = mapper.readTree(call("GET", "/api/v1/accounts/me/addresses?pageSize=1", alice, null).body());
        assertThat(page.get("data").size()).isEqualTo(1);
        assertThat(page.get("pagination").get("hasNext").asBoolean()).isTrue();
        var next = mapper.readTree(call("GET", "/api/v1/accounts/me/addresses?pageSize=1&cursor="
                + page.get("pagination").get("nextCursor").asString(), alice, null).body());
        assertThat(next.get("data").size()).isEqualTo(1);
        assertThat(next.get("data").get(0).get("addressId")).isNotEqualTo(page.get("data").get(0).get("addressId"));
        assertThat(next.get("pagination").get("hasNext").asBoolean()).isFalse();
        try (var admin = com.fresveg.testing.database.DatabaseFixture.admin(POSTGRES)) {
            assertThat(com.fresveg.testing.database.DatabaseFixture.scalar(admin,
                    "SELECT (created_by=updated_by AND created_by='" + account.get("userId").asString()
                    + "' AND customer_id='" + account.get("customerId").asString()
                    + "' AND updated_at>=created_at AND version=1)::text FROM account.addresses WHERE address_id='"
                    + address.get("addressId").asString() + "'")).isEqualTo("true");
        }
    }

    @Test
    void concurrentAddressWritesAllowOnlyOneVersionWinner() throws Exception {
        String token = TestIdentityProvider.token(UUID.randomUUID().toString());
        var row = data(call("POST", "/api/v1/accounts/me/addresses", token, address("Original", null)));
        String path = "/api/v1/accounts/me/addresses/" + row.get("addressId").asString();
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var one = executor.submit(() -> { start.await(); return call("PUT", path, token, address("Winner one", 0L)); });
            var two = executor.submit(() -> { start.await(); return call("PUT", path, token, address("Winner two", 0L)); });
            start.countDown();
            assertThat(java.util.List.of(one.get().statusCode(), two.get().statusCode())).containsExactlyInAnyOrder(200, 409);
        }
        var stored = data(call("GET", "/api/v1/accounts/me/addresses", token, null)).get(0);
        assertThat(stored.get("version").asLong()).isEqualTo(1);
        assertThat(stored.get("line1").asString()).isIn("Winner one", "Winner two");
    }

    @Test
    void vendorMembershipsSupportMultipleStaffAndVendorsWithoutLeakingOtherAccounts() throws Exception {
        String alice = TestIdentityProvider.token(UUID.randomUUID().toString());
        String bob = TestIdentityProvider.token(UUID.randomUUID().toString());
        String outsider = TestIdentityProvider.token(UUID.randomUUID().toString());
        String a = data(call("GET", "/api/v1/accounts/me", alice, null)).get("userId").asString();
        String b = data(call("GET", "/api/v1/accounts/me", bob, null)).get("userId").asString();
        try (var admin = com.fresveg.testing.database.DatabaseFixture.admin(POSTGRES)) {
            String shared = vendor(admin, "ACTIVE");
            membership(admin, shared, a, "VENDOR_ADMIN", "ACTIVE");
            membership(admin, shared, b, "VENDOR_STAFF", "ACTIVE");
            membership(admin, vendor(admin, "ACTIVE"), a, "VENDOR_STAFF", "ACTIVE");
            membership(admin, vendor(admin, "SUSPENDED"), a, "VENDOR_STAFF", "ACTIVE");
            membership(admin, vendor(admin, "ACTIVE"), a, "VENDOR_STAFF", "INACTIVE");
            membership(admin, vendor(admin, "ACTIVE"), b, "VENDOR_ADMIN", "ACTIVE");
        }
        var all = data(call("GET", "/api/v1/accounts/me/vendor-memberships", alice, null));
        assertThat(all.size()).isEqualTo(2);
        assertThat(all.toString()).contains("VENDOR_ADMIN", "VENDOR_STAFF");
        assertThat(data(call("GET", "/api/v1/accounts/me/vendor-memberships", bob, null)).size()).isEqualTo(2);
        assertThat(data(call("GET", "/api/v1/accounts/me/vendor-memberships", outsider, null)).size()).isZero();
        var page = mapper.readTree(call("GET", "/api/v1/accounts/me/vendor-memberships?pageSize=1", alice, null).body());
        assertThat(page.get("pagination").get("hasNext").asBoolean()).isTrue();
        var next = mapper.readTree(call("GET", "/api/v1/accounts/me/vendor-memberships?pageSize=1&cursor="
                + page.get("pagination").get("nextCursor").asString(), alice, null).body());
        assertThat(next.get("data").size()).isEqualTo(1);
        assertThat(next.get("data").get(0).get("membershipId")).isNotEqualTo(page.get("data").get(0).get("membershipId"));
        assertThat(next.get("pagination").get("hasNext").asBoolean()).isFalse();
    }

    @Test
    void suspendedAccountsAndCustomerProfilesCannotUseCustomerResources() throws Exception {
        String token = TestIdentityProvider.token(UUID.randomUUID().toString());
        String user = data(call("GET", "/api/v1/accounts/me", token, null)).get("userId").asString();
        try (var admin = com.fresveg.testing.database.DatabaseFixture.admin(POSTGRES)) {
            execute(admin, "UPDATE account.customer_profiles SET status='SUSPENDED' WHERE user_id='" + user + "'");
            assertProblem(call("POST", "/api/v1/accounts/me/addresses", token, address("Suspended", null)), 403);
            assertProblem(call("GET", "/api/v1/accounts/me/addresses", token, null), 403);
            execute(admin, "UPDATE account.users SET status='SUSPENDED' WHERE user_id='" + user + "'");
            assertProblem(call("GET", "/api/v1/accounts/me", token, null), 403);
            assertProblem(call("GET", "/api/v1/accounts/me/vendor-memberships", token, null), 403);
        }
    }

    @Test
    void generatedOpenApiDescribesOnlyTheFiveAccountOperations() throws Exception {
        var response = get("/v3/api-docs", "openapi-check");
        assertThat(response.statusCode()).isEqualTo(200);
        var spec = mapper.readTree(response.body());
        assertThat(spec.get("openapi").asString()).startsWith("3.1");
        assertThat(spec.get("paths").size()).isEqualTo(5);
        assertThat(spec.get("paths").get("/api/v1/accounts/me/addresses").has("post")).isTrue();
        assertThat(spec.get("paths").get("/api/v1/accounts/me/addresses").get("post").get("responses").has("201")).isTrue();
        assertThat(spec.get("components").get("schemas").get("CreateAddressRequest").get("additionalProperties").asBoolean()).isFalse();
        assertThat(spec.get("components").get("securitySchemes").get("bearerAuth").get("scheme").asString()).isEqualTo("bearer");
        for (var reference : spec.findValues("$ref")) {
            String pointer = reference.asString();
            assertThat(pointer).startsWith("#/");
            assertThat(spec.at(pointer.substring(1)).isMissingNode()).as(pointer).isFalse();
        }
        var yaml = get("/v3/api-docs.yaml", "openapi-check");
        assertThat(yaml.statusCode()).isEqualTo(200);
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/account-api.yaml"), yaml.body());
    }

    private HttpResponse<String> call(String method, String path, String token, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20)).header("X-Correlation-ID", "account-test");
        if (token != null) { request.header("Authorization", "Bearer " + token); }
        if (body != null) { request.header("Content-Type", "application/json"); }
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private tools.jackson.databind.JsonNode data(HttpResponse<String> response) {
        assertThat(response.statusCode()).withFailMessage(response.body()).isIn(200, 201);
        var body = mapper.readTree(response.body());
        assertThat(body.get("meta").get("requestId").asString()).isEqualTo("account-test");
        assertThat(Instant.parse(body.get("meta").get("timestamp").asString())).isNotNull();
        return body.get("data");
    }

    private void assertProblem(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("content-type")).contains("application/problem+json");
        var problem = mapper.readTree(response.body());
        assertThat(problem.get("status").asInt()).isEqualTo(status);
        assertThat(problem.get("correlationId").asString()).isEqualTo("account-test");
        assertThat(problem.has("code")).isTrue();
        assertThat(problem.has("instance")).isTrue();
        assertThat(problem.toString()).doesNotContain("SQLException", "Bearer ", "oidc_subject");
    }

    @Test void vendorDirectoryReturnsOnlyActiveBusinessIdentity()throws Exception {
        String token=TestIdentityProvider.token(UUID.randomUUID().toString());
        try(var db=com.fresveg.testing.database.DatabaseFixture.admin(POSTGRES)) {
            String active=vendor(db,"ACTIVE"),inactive=vendor(db,"SUSPENDED");
            var response=data(call("GET","/api/v1/accounts/vendors/"+active,token,null));assertThat(response.size()).isEqualTo(2);assertThat(response.path("vendorId").asString()).isEqualTo(active);assertThat(response.path("name").asString()).isNotBlank();
            assertProblem(call("GET","/api/v1/accounts/vendors/"+inactive,token,null),404);assertProblem(call("GET","/api/v1/accounts/vendors/"+UUID.randomUUID(),token,null),404);assertProblem(call("GET","/api/v1/accounts/vendors/"+active,null,null),401);
        }
    }

    private String address(String street, Long version) {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("label", "Home"); body.put("recipientName", "Test Customer"); body.put("line1", street);
        body.put("city", "Boston"); body.put("postalCode", "02110"); body.put("countryCode", "US");
        if (version != null) { body.put("version", version); }
        return mapper.writeValueAsString(body);
    }

    private static void execute(java.sql.Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }
    private static String vendor(java.sql.Connection connection, String status) throws Exception {
        String id = UUID.randomUUID().toString();
        execute(connection, "INSERT INTO account.vendors(vendor_id,vendor_code,name,status) VALUES ('" + id + "','" + id + "','Test Vendor','" + status + "')");
        return id;
    }
    private static void membership(java.sql.Connection connection, String vendor, String user, String role, String status) throws Exception {
        execute(connection, "INSERT INTO account.vendor_users(vendor_user_id,vendor_id,user_id,membership_role,status,created_by,updated_by) "
                + "VALUES (gen_random_uuid(),'" + vendor + "','" + user + "','" + role + "','" + status + "','" + user + "','" + user + "')");
    }

    private HttpResponse<String> get(String path, String correlationId) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10));
        if (correlationId != null) {
            request.header(CorrelationIds.HEADER, correlationId);
        }
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    record SerializationProbe(String requestId, Instant occurredAt, BigDecimal amount) {
    }
}
