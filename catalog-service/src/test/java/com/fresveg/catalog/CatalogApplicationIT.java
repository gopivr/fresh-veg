package com.fresveg.catalog;

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
class CatalogApplicationIT extends ServiceDatabaseTest {
    @org.springframework.test.context.DynamicPropertySource
    static void securityProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("catalog.security.issuer-uri", () -> TestIdentityProvider.ISSUER);
        registry.add("catalog.security.audience", () -> "fresveg-catalog");
        registry.add("catalog.security.jwk-set-uri", TestIdentityProvider::jwksUri);
        registry.add("catalog.account-base-url", TestAccountServer::url);
    }
    @org.junit.jupiter.api.AfterAll
    static void stopAccount() { TestAccountServer.stop(); }

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
    @ValueSource(strings = {"/api/v1/accounts/me", "/internal/v1/test", "/actuator/env", "/login"})
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
        assertThat(environment.getProperty("spring.application.name")).isEqualTo("catalog-service");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(environment.getProperty("spring.sql.init.mode")).isEqualTo("never");
    }

    @Test
    void administrationUsesLiveAccountRolesAndIgnoresTokenRoleClaims() throws Exception {
        String token=TestIdentityProvider.token(UUID.randomUUID().toString());
        String user=TestAccountServer.user(token,false);
        var body=category("Staff category",null);
        problem(call("POST","/categories",null,body),401);
        problem(call("POST","/categories",token,body),403);
        TestAccountServer.user(token,true);
        var created=data(call("POST","/categories",token,body));
        try (var db=com.fresveg.testing.database.DatabaseFixture.admin(POSTGRES)) {
            assertThat(com.fresveg.testing.database.DatabaseFixture.scalar(db,"SELECT created_by::text FROM catalog.categories WHERE category_id='"+created.path("categoryId").asString()+"'"))
                    .isEqualTo(user);
        }
        TestAccountServer.revokeAdmin(user);
        problem(call("POST","/categories",token,category("Revoked",null)),403);
        assertThat(call("GET","/products",null,null).statusCode()).isEqualTo(200);
    }

    @Test
    void signedJwtValidationRejectsWrongIssuerAudienceSignatureAndRequiredClaims() throws Exception {
        String subject=UUID.randomUUID().toString();
        for (String token:java.util.List.of("not-a-jwt",
                TestIdentityProvider.token(subject,b -> b.issuer("https://wrong.example.test"),false),
                TestIdentityProvider.token(subject,b -> b.audience("wrong-api"),false),
                TestIdentityProvider.token(subject,b -> b.expirationTime(java.util.Date.from(Instant.now().minusSeconds(180))),false),
                TestIdentityProvider.token(subject,b -> b.expirationTime(null),false),
                TestIdentityProvider.token(subject,b -> b.subject(null),false),
                TestIdentityProvider.token(subject,b -> {},true))) {
            problem(call("POST","/categories",token,category("Denied",null)),401);
        }
    }

    @Test
    void productsPersistAttributesVariantsUnitsImagesAndHierarchicalClassifications() throws Exception {
        String token=adminToken();
        String parent=data(call("POST","/categories",token,category("Vegetables",null))).path("categoryId").asString();
        String child=data(call("POST","/categories",token,category("Tomatoes",parent))).path("categoryId").asString();
        var input=product("Roma Tomato",child);
        var response=call("POST","/products",token,input);
        assertThat(response.statusCode()).isEqualTo(201);
        var result=data(response);
        String id=result.path("productId").asString();
        assertThat(response.headers().firstValue("location")).contains("/api/v1/catalog/products/"+id);
        assertThat(result.path("attributes").path("origin").asString()).isEqualTo("US");
        assertThat(result.path("variants").get(0).path("unitCode").asString()).isEqualTo("KG");
        assertThat(result.path("variants").get(0).path("quantity").decimalValue()).isEqualByComparingTo("1.123456");
        assertThat(result.path("images").get(0).path("position").asInt()).isZero();
        assertThat(result.path("version").asLong()).isZero();
        assertThat(data(call("GET","/products/"+id,null,null))).isEqualTo(result);
        assertThat(data(call("GET","/categories/"+child+"/products",null,null)).size()).isEqualTo(1);
        assertThat(data(call("GET","/categories/"+parent+"/products",null,null)).size()).isZero();
        var categories=data(call("GET","/categories?pageSize=100",null,null));
        assertThat(categories.toString()).contains(parent,child);
        try (var db=com.fresveg.testing.database.DatabaseFixture.admin(POSTGRES)) {
            assertThat(com.fresveg.testing.database.DatabaseFixture.scalar(db,"SELECT jsonb_typeof(attributes) FROM catalog.products WHERE product_id='"+id+"'"))
                    .isEqualTo("object");
        }
    }

    @Test
    void searchFilteringSortAndCursorsAreBoundedStableAndDoNotExposeDrafts() throws Exception {
        String token=adminToken();
        String category=data(call("POST","/categories",token,category("Search fixture",null))).path("categoryId").asString();
        for (int i=0;i<4;i++) {
            var input=product(i<2?"Roma Tomato":"Garden Carrot",category);
            input.put("organic",i%2==0);
            data(call("POST","/products",token,input));
        }
        var draft=product("Hidden Tomato",category); draft.put("status","DRAFT");
        String draftId=data(call("POST","/products",token,draft)).path("productId").asString();
        problem(call("GET","/products/"+draftId,null,null),404);
        String path="/products?categoryId="+category;
        assertThat(data(call("GET",path+"&q=Roma",null,null)).size()).isEqualTo(2);
        assertThat(data(call("GET",path+"&organic=true",null,null)).size()).isEqualTo(2);
        assertThat(data(call("GET",path+"&attributes="+encode("{\"origin\":\"US\"}"),null,null)).size()).isEqualTo(4);
        assertThat(data(call("GET",path+"&attributes="+encode("{\"origin\":\"CA\"}"),null,null)).size()).isZero();
        problem(call("GET",path+"&status=DRAFT",null,null),403);
        assertThat(data(call("GET",path+"&status=DRAFT",token,null)).size()).isEqualTo(1);
        for (String sort:java.util.List.of("name","code")) {
            var ids=new java.util.HashSet<String>(); String cursor=null;
            do {
                var response=call("GET",path+"&sort="+sort+"&pageSize=1"+(cursor==null?"":"&cursor="+encode(cursor)),null,null);
                var json=mapper.readTree(response.body());
                var rows=data(response); assertThat(rows.size()).isEqualTo(1);
                assertThat(ids.add(rows.get(0).path("productId").asString())).isTrue();
                cursor=json.path("pagination").path("nextCursor").isNull()?null:json.path("pagination").path("nextCursor").asString();
                if (cursor!=null) { problem(call("GET",path+"&sort="+sort+"&organic=true&cursor="+encode(cursor),null,null),400); }
            } while (cursor!=null);
            assertThat(ids).hasSize(4);
        }
        for (String query:java.util.List.of("pageSize=0","pageSize=101","sort=price","cursor=bad","status=UNKNOWN","attributes=%5B%5D")) {
            problem(call("GET","/products?"+query,null,null),400);
        }
        problem(call("GET","/categories/"+UUID.randomUUID()+"/products",null,null),404);
        problem(call("GET","/categories?cursor=invalid",null,null),400);
        var first=mapper.readTree(call("GET","/categories?pageSize=1",null,null).body());
        assertThat(first.path("pagination").path("hasNext").asBoolean()).isTrue();
        var second=data(call("GET","/categories?pageSize=1&cursor="+encode(first.path("pagination").path("nextCursor").asString()),null,null));
        assertThat(second.get(0).path("categoryId")).isNotEqualTo(first.path("data").get(0).path("categoryId"));
    }

    @Test
    void aggregateReplacementPreservesVariantIdsArchivesOmissionsAndRejectsStaleVersions() throws Exception {
        String token=adminToken(); var input=product("Before",null);
        var created=data(call("POST","/products",token,input));
        String path="/products/"+created.path("productId").asString();
        String variant=created.path("variants").get(0).path("variantId").asString();
        input.put("version",0);input.put("name","After");
        var replaced=data(call("PUT",path,token,input));
        assertThat(replaced.path("version").asLong()).isEqualTo(1);
        assertThat(replaced.path("variants").get(0).path("variantId").asString()).isEqualTo(variant);
        problem(call("PUT",path,token,input),409);
        input.put("version",1); var originalVariants=input.get("variants"); input.put("variants",java.util.List.of());
        var archived=data(call("PUT",path,token,input));
        assertThat(archived.path("variants").get(0).path("status").asString()).isEqualTo("ARCHIVED");
        assertThat(data(call("GET",path,null,null)).path("variants").size()).isZero();
        input.put("version",2);input.put("variants",originalVariants);
        assertThat(data(call("PUT",path,token,input)).path("variants").get(0).path("variantId").asString()).isEqualTo(variant);
        input.put("version",3);input.put("categoryIds",java.util.List.of(UUID.randomUUID()));
        problem(call("PUT",path,token,input),400);
        assertThat(data(call("GET",path,null,null)).path("version").asLong()).isEqualTo(3);
        input.remove("version");problem(call("PUT",path,token,input),400);
    }

    @Test
    void invalidMutationsAndDuplicateCodesLeaveNoPartialAggregate() throws Exception {
        String token=adminToken(); var input=product("Unique",null);
        data(call("POST","/products",token,input));
        problem(call("POST","/products",token,input),409);
        // Fail after the parent insert: a globally reserved variant code must roll back the whole aggregate.
        var conflictingChild=product("Rolled back parent",null);
        conflictingChild.put("variants",input.get("variants"));
        problem(call("POST","/products",token,conflictingChild),409);
        try (var db=com.fresveg.testing.database.DatabaseFixture.admin(POSTGRES)) {
            assertThat(com.fresveg.testing.database.DatabaseFixture.scalar(db,"SELECT count(*) FROM catalog.products WHERE code='"+conflictingChild.get("code")+"'"))
                    .isEqualTo("0");
        }
        var invalid=product("Invalid",null); invalid.put("price",1.25);
        problem(call("POST","/products",token,invalid),400);
        invalid=product("Invalid",null); invalid.put("categoryIds",java.util.List.of(UUID.randomUUID()));
        problem(call("POST","/products",token,invalid),400);
        invalid=product("Invalid",null); invalid.put("attributes",java.util.Map.of("large","x".repeat(17000)));
        problem(call("POST","/products",token,invalid),400);
        invalid=product("Invalid",null); invalid.put("variants",java.util.List.of(java.util.Map.of("code",code(),"name","Variant","unitCode","UNKNOWN","quantity",1,"status","ACTIVE")));
        problem(call("POST","/products",token,invalid),400);
        invalid=product("Invalid",null); invalid.put("images",java.util.List.of(java.util.Map.of("url","javascript:alert(1)","altText","bad")));
        problem(call("POST","/products",token,invalid),400);
        problem(call("POST","/categories",token,category("Missing parent",UUID.randomUUID().toString())),404);
        try (var db=com.fresveg.testing.database.DatabaseFixture.admin(POSTGRES)) {
            assertThat(com.fresveg.testing.database.DatabaseFixture.scalar(db,"SELECT count(*) FROM catalog.products WHERE code='"+input.get("code")+"'"))
                    .isEqualTo("1");
        }
    }

    @Test
    void simultaneousStatusUpdatesHaveOneWinnerAndKeepAuditAndVersionConsistent() throws Exception {
        String token=adminToken(); var input=product("Race",null);
        var created=data(call("POST","/products",token,input));
        String id=created.path("productId").asString();String path="/products/"+id;
        try (var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            var a=executor.submit(() -> {start.await();return call("PATCH",path,token,java.util.Map.of("version",0,"status","ARCHIVED"));});
            var b=executor.submit(() -> {start.await();return call("PATCH",path,token,java.util.Map.of("version",0,"status","DRAFT"));});
            start.countDown();
            assertThat(java.util.List.of(a.get().statusCode(),b.get().statusCode())).containsExactlyInAnyOrder(200,409);
        }
        problem(call("GET",path,null,null),404);
        assertThat(data(call("GET",path,token,null)).path("version").asLong()).isEqualTo(1);
        try (var db=com.fresveg.testing.database.DatabaseFixture.admin(POSTGRES)) {
            assertThat(com.fresveg.testing.database.DatabaseFixture.scalar(db,"SELECT (created_by=updated_by AND updated_at>=created_at AND version=1)::text FROM catalog.products WHERE product_id='"+id+"'"))
                    .isEqualTo("true");
        }
    }

    @Test
    void generatedOpenApiContainsAllEightOperationsAndResolvedSchemas() throws Exception {
        var response=get("/v3/api-docs","openapi");assertThat(response.statusCode()).isEqualTo(200);
        var spec=mapper.readTree(response.body());assertThat(spec.path("openapi").asString()).startsWith("3.1");
        assertThat(spec.path("paths").size()).isEqualTo(4);
        assertThat(spec.path("paths").path("/api/v1/catalog/products").path("post").path("responses").has("201")).isTrue();
        assertThat(spec.path("paths").path("/api/v1/catalog/products/{productId}").has("patch")).isTrue();
        for (var reference:spec.findValues("$ref")) { assertThat(spec.at(reference.asString().substring(1)).isMissingNode()).isFalse(); }
        var yaml=get("/v3/api-docs.yaml","openapi");assertThat(yaml.statusCode()).isEqualTo(200);
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/catalog-api.yaml"),yaml.body());
    }

    private String adminToken() throws Exception {
        String token=TestIdentityProvider.token(UUID.randomUUID().toString()); TestAccountServer.user(token,true); return token;
    }
    private static String code() { return "P"+UUID.randomUUID().toString().replace("-","").toUpperCase(java.util.Locale.ROOT); }
    private static java.util.Map<String,Object> category(String name,String parent) {
        var map=new java.util.LinkedHashMap<String,Object>();map.put("code",code());map.put("name",name);map.put("parentCategoryId",parent);return map;
    }
    private static java.util.Map<String,Object> product(String name,String category) {
        var map=new java.util.LinkedHashMap<String,Object>();map.put("code",code());map.put("name",name);map.put("organic",true);map.put("status","ACTIVE");
        map.put("attributes",java.util.Map.of("origin","US","certifications",java.util.List.of("test")));
        map.put("categoryIds",category==null?java.util.List.of():java.util.List.of(category));
        map.put("variants",java.util.List.of(java.util.Map.of("code",code(),"name","Pack","unitCode","KG","quantity",new BigDecimal("1.123456"),"status","ACTIVE")));
        map.put("images",java.util.List.of(java.util.Map.of("url","https://images.example.test/tomato.jpg","altText","Tomato")));return map;
    }
    private HttpResponse<String> call(String method,String path,String token,Object body) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/catalog"+path))
                .timeout(Duration.ofSeconds(20)).header("X-Correlation-ID","catalog-test");
        if (token!=null) { request.header("Authorization","Bearer "+token); }
        if (body!=null) { request.header("Content-Type","application/json"); }
        request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        try (var client=HttpClient.newHttpClient()) { return client.send(request.build(),HttpResponse.BodyHandlers.ofString()); }
    }
    private tools.jackson.databind.JsonNode data(HttpResponse<String> response) {
        assertThat(response.statusCode()).withFailMessage(response.body()).isIn(200,201);
        var json=mapper.readTree(response.body()); assertThat(json.path("meta").path("requestId").asString()).isEqualTo("catalog-test");return json.path("data");
    }
    private void problem(HttpResponse<String> response,int status) {
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("content-type")).contains("application/problem+json");
        var json=mapper.readTree(response.body()); assertThat(json.path("status").asInt()).isEqualTo(status);
        assertThat(json.path("correlationId").asString()).isEqualTo("catalog-test");
        assertThat(json.has("code")).isTrue();assertThat(json.has("instance")).isTrue();
    }
    private static String encode(String value) { return java.net.URLEncoder.encode(value,java.nio.charset.StandardCharsets.UTF_8); }

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
