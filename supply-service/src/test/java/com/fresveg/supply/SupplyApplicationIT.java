package com.fresveg.supply;

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
import io.micrometer.core.instrument.MeterRegistry;
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
@org.springframework.context.annotation.Import(SupplyApplicationIT.PricingClock.class)
class SupplyApplicationIT extends ServiceDatabaseTest {
    @org.springframework.test.context.DynamicPropertySource
    static void securityProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("supply.inventory.service-subjects", () -> "test-commerce,test-other");
        registry.add("supply.security.issuer-uri", () -> TestIdentityProvider.ISSUER);
        registry.add("supply.security.audience", () -> "fresveg-supply");
        registry.add("supply.security.jwk-set-uri", TestIdentityProvider::jwksUri);
        registry.add("supply.account-base-url", TestAccountServer::url);
        registry.add("supply.catalog-base-url", TestCatalogServer::url);
    }
    @org.junit.jupiter.api.AfterAll
    static void stopOwners() { TestCatalogServer.stop();TestAccountServer.stop(); }

    @LocalServerPort
    int port;
    @Autowired
    JsonMapper mapper;
    @Autowired
    Environment environment;
    @Autowired
    MeterRegistry meterRegistry;

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
    void prometheusEndpointPublishesOperationalMetrics() throws Exception {
        assertThat(meterRegistry.find("fresveg.inventory.reservation.conflicts").counter()).isNotNull();
        var response = get("/actuator/prometheus", "metrics-probe");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue(CorrelationIds.HEADER)).contains("metrics-probe");
        assertThat(response.body()).contains("http_server_requests", "application=\"" + environment.getProperty("spring.application.name") + "\"");
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
        assertThat(environment.getProperty("spring.application.name")).isEqualTo("supply-service");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(environment.getProperty("spring.sql.init.mode")).isEqualTo("never");
    }

    static final Instant NOW=Instant.parse("2026-09-08T12:00:00Z");
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    static class PricingClock {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        java.time.Clock testClock() { return java.time.Clock.fixed(NOW,java.time.ZoneOffset.UTC); }
    }

    @Test
    void vendorAuthorityComesFromStoredMembershipAndRevocationTakesEffect() throws Exception {
        var fixture=fixture();String staff=TestIdentityProvider.token(UUID.randomUUID().toString());
        TestAccountServer.membership(staff,fixture.vendor(),"VENDOR_STAFF");
        assertThat(data(call("GET","/vendor/locations?vendorId="+fixture.vendor(),staff,null)).size()).isEqualTo(1);
        problem(call("POST","/vendor/locations?vendorId="+fixture.vendor(),staff,location()),403);
        String outsider=TestIdentityProvider.token(UUID.randomUUID().toString());TestAccountServer.user(outsider,true);
        problem(call("GET","/vendor/listings?vendorId="+fixture.vendor(),outsider,null),403);
        problem(call("POST","/vendor/locations?vendorId="+fixture.vendor(),null,location()),401);
        UUID second=TestAccountServer.vendor(fixture.token(),"VENDOR_ADMIN");
        data(call("POST","/vendor/locations?vendorId="+second,fixture.token(),location()));
        assertThat(data(call("GET","/vendor/locations?vendorId="+second,fixture.token(),null)).size()).isEqualTo(1);
        TestAccountServer.revoke(staff,fixture.vendor());
        problem(call("GET","/vendor/locations?vendorId="+fixture.vendor(),staff,null),403);
        TestAccountServer.revoke(fixture.token(),fixture.vendor());
        problem(call("POST","/vendor/listings?vendorId="+fixture.vendor(),fixture.token(),listing(fixture)),403);
    }

    @Test
    void signedTokensRequireTheCorrectIssuerAudienceSignatureAndExpiry() throws Exception {
        String subject=UUID.randomUUID().toString();
        for (String token:java.util.List.of("malformed",
                TestIdentityProvider.token(subject,b -> b.issuer("https://other.example.test"),false),
                TestIdentityProvider.token(subject,b -> b.audience("other-api"),false),
                TestIdentityProvider.token(subject,b -> b.expirationTime(java.util.Date.from(Instant.now().minusSeconds(180))),false),
                TestIdentityProvider.token(subject,b -> b.expirationTime(null),false),
                TestIdentityProvider.token(subject,b -> b.subject(null),false),
                TestIdentityProvider.token(subject,b -> {},true))) {
            problem(call("GET","/vendor/listings?vendorId="+UUID.randomUUID(),token,null),401);
        }
    }

    @Test
    void effectivePricesAndTiersUseExactCurrencyQuantityAndHalfOpenWindows() throws Exception {
        var fixture=fixture();var input=listing(fixture);
        var expired=price("USD","9.000000",NOW.minusSeconds(3600),NOW);
        var current=price("USD","2.750001",NOW,NOW.plusSeconds(3600));
        current.put("tiers",java.util.List.of(tier("5","2.500000"),tier("10","2.125001")));
        input.put("prices",java.util.List.of(expired,current,price("USD","8.000000",NOW.plusSeconds(3600),null),price("EUR","3.123456",NOW,null)));
        var response=call("POST","/vendor/listings?vendorId="+fixture.vendor(),fixture.token(),input);
        assertThat(response.statusCode()).isEqualTo(201);var created=data(response);String id=created.path("listingId").asString();
        assertThat(response.headers().firstValue("location")).contains("/api/v1/supply/vendor/listings/"+id);
        assertThat(created.path("prices").size()).isEqualTo(4);
        assertThat(data(call("GET","/listings/"+id,null,null)).path("prices").size()).isEqualTo(2);
        for (String[] expected:new String[][] {{"1","2.750001"},{"5","2.500000"},{"10","2.125001"},{"100","2.125001"}}) {
            var offer=data(call("GET",offers(fixture,"USD",expected[0]),null,null)).get(0);
            assertThat(offer.path("unitPrice").decimalValue()).isEqualByComparingTo(expected[1]);
            assertThat(offer.path("evaluatedAt").asString()).isEqualTo(NOW.toString());
            assertThat(offer.path("currency").asString()).isEqualTo("USD");
            assertThat(offer.has("availableQuantity")).isFalse();
        }
        assertThat(data(call("GET",offers(fixture,"USD","0.5"),null,null)).size()).isZero();
        assertThat(data(call("GET",offers(fixture,"EUR","1"),null,null)).get(0).path("unitPrice").decimalValue()).isEqualByComparingTo("3.123456");
        try (var db=com.fresveg.testing.database.DatabaseFixture.admin(POSTGRES)) {
            assertThat(com.fresveg.testing.database.DatabaseFixture.scalar(db,"SELECT unit_price FROM supply.vendor_listing_prices WHERE listing_id='"+id+"' AND currency='USD' AND valid_from='"+NOW+"'"))
                    .isEqualTo("2.750001");
            assertThat(com.fresveg.testing.database.DatabaseFixture.scalar(db,"SELECT created_by::text FROM supply.vendor_listings WHERE listing_id='"+id+"'"))
                    .isEqualTo(TestAccountServer.user(fixture.token(),false));
        }
    }

    @Test
    void differentVendorsCannotReadOrMutatePrivateListingsOrReuseForeignLocations() throws Exception {
        var a=fixture();var b=fixture();var input=listing(a);
        String id=data(call("POST","/vendor/listings?vendorId="+a.vendor(),a.token(),input)).path("listingId").asString();
        var foreign=call("GET","/vendor/listings/"+id,b.token(),null);
        var absent=call("GET","/vendor/listings/"+java.util.UUID.randomUUID(),b.token(),null);
        problem(foreign,404);problem(absent,404);
        assertThat(mapper.readTree(foreign.body()).path("detail")).isEqualTo(mapper.readTree(absent.body()).path("detail"));
        input.put("version",0);problem(call("PUT","/vendor/listings/"+id,b.token(),input),404);
        var other=listing(b);other.put("locationId",a.location());
        problem(call("POST","/vendor/listings?vendorId="+b.vendor(),b.token(),other),400);
        var loc=location();loc.put("version",0);problem(call("PUT","/vendor/locations/"+a.location(),b.token(),loc),404);
        assertThat(data(call("GET","/vendor/listings?vendorId="+b.vendor(),b.token(),null)).size()).isZero();
        var injected=listing(a);injected.put("vendorId",b.vendor());
        problem(call("POST","/vendor/listings?vendorId="+a.vendor(),a.token(),injected),400);
    }

    @Test
    void scheduleReplacementRetainsHistoryAndRejectsMutatingExistingPriceIdsAtomically() throws Exception {
        var fixture=fixture();var input=listing(fixture);
        var created=data(call("POST","/vendor/listings?vendorId="+fixture.vendor(),fixture.token(),input));
        String id=created.path("listingId").asString();String original=created.path("prices").get(0).path("priceId").asString();
        var unchanged=price("USD","2.750001",NOW,null);unchanged.put("priceId",original);
        input.put("version",0);input.put("prices",java.util.List.of(unchanged));
        assertThat(data(call("PUT","/vendor/listings/"+id,fixture.token(),input)).path("version").asLong()).isEqualTo(1);
        input.put("version",1);unchanged.put("unitPrice",new BigDecimal("1.111111"));
        problem(call("PUT","/vendor/listings/"+id,fixture.token(),input),400);
        assertThat(data(call("GET","/vendor/listings/"+id,fixture.token(),null)).path("version").asLong()).isEqualTo(1);
        var replacement=price("USD","1.111111",NOW,null);replacement.put("tiers",java.util.List.of(tier("5","1.000001")));
        input.put("prices",java.util.List.of(replacement));
        var updated=data(call("PUT","/vendor/listings/"+id,fixture.token(),input));
        assertThat(updated.path("version").asLong()).isEqualTo(2);
        assertThat(updated.path("prices").get(0).path("priceId").asString()).isNotEqualTo(original);
        problem(call("PUT","/vendor/listings/"+id,fixture.token(),input),409);
        try (var db=com.fresveg.testing.database.DatabaseFixture.admin(POSTGRES)) {
            assertThat(com.fresveg.testing.database.DatabaseFixture.scalar(db,"SELECT count(*) FROM supply.vendor_listing_prices WHERE listing_id='"+id+"'"))
                    .isEqualTo("2");
            assertThat(com.fresveg.testing.database.DatabaseFixture.scalar(db,"SELECT status FROM supply.vendor_listing_prices WHERE price_id='"+original+"'"))
                    .isEqualTo("CANCELLED");
        }
    }

    @Test
    void invalidSchedulesReferencesAndQuantitiesDoNotCreatePartialListings() throws Exception {
        var fixture=fixture();String endpoint="/vendor/listings?vendorId="+fixture.vendor();
        var invalid=listing(fixture);invalid.put("prices",java.util.List.of(price("USD","1",NOW,null),price("USD","2",NOW.plusSeconds(1),null)));
        problem(call("POST",endpoint,fixture.token(),invalid),400);
        invalid=listing(fixture);var p=price("USD","1",NOW,null);p.put("tiers",java.util.List.of(tier("5","2")));invalid.put("prices",java.util.List.of(p));
        problem(call("POST",endpoint,fixture.token(),invalid),400);
        invalid=listing(fixture);invalid.put("prices",java.util.List.of(price("ZZZ","1",NOW,null)));problem(call("POST",endpoint,fixture.token(),invalid),400);
        invalid=listing(fixture);invalid.put("prices",java.util.List.of(price("USD","1",NOW,NOW)));problem(call("POST",endpoint,fixture.token(),invalid),400);
        invalid=listing(fixture);invalid.put("minimumOrderQuantity",new BigDecimal("0.0000001"));problem(call("POST",endpoint,fixture.token(),invalid),400);
        invalid=listing(fixture);invalid.put("uomCode","EA");problem(call("POST",endpoint,fixture.token(),invalid),400);
        invalid=listing(fixture);invalid.put("variantId",UUID.randomUUID());problem(call("POST",endpoint,fixture.token(),invalid),400);
        invalid=listing(fixture);invalid.put("productId",UUID.randomUUID());problem(call("POST",endpoint,fixture.token(),invalid),404);
        // Failure after the listing insert must roll back that parent and all dependent work.
        invalid=listing(fixture);p=price("USD","1",NOW,null);p.put("priceId",UUID.randomUUID());invalid.put("prices",java.util.List.of(p));
        problem(call("POST",endpoint,fixture.token(),invalid),400);
        assertThat(data(call("GET","/vendor/listings?vendorId="+fixture.vendor(),fixture.token(),null)).size()).isZero();
        for (String[] args:new String[][] {{"ZZZ","1"},{"usd","1"},{"USD","0"},{"USD","-1"},{"USD","0.0000001"}}) {
            problem(call("GET",offers(fixture,args[0],args[1]),null,null),400);
        }
    }

    @Test
    void listingLocationAndCatalogVisibilityControlOffersWithoutInventingStock() throws Exception {
        var fixture=fixture();var input=listing(fixture);input.put("status","DRAFT");
        String id=data(call("POST","/vendor/listings?vendorId="+fixture.vendor(),fixture.token(),input)).path("listingId").asString();
        problem(call("GET","/listings/"+id,null,null),404);
        assertThat(data(call("GET",offers(fixture,"USD","1"),null,null)).size()).isZero();
        input.put("status","ACTIVE");input.put("version",0);data(call("PUT","/vendor/listings/"+id,fixture.token(),input));
        var loc=location();loc.put("version",0);loc.put("status","INACTIVE");
        data(call("PUT","/vendor/locations/"+fixture.location(),fixture.token(),loc));
        problem(call("GET","/listings/"+id,null,null),404);
        assertThat(data(call("GET",offers(fixture,"USD","1"),null,null)).size()).isZero();
        loc.put("version",1);loc.put("status","ACTIVE");data(call("PUT","/vendor/locations/"+fixture.location(),fixture.token(),loc));
        assertThat(data(call("GET",offers(fixture,"USD","1"),null,null)).size()).isEqualTo(1);
        TestCatalogServer.archive(fixture.product().path("productId").asString(),0);
        problem(call("GET",offers(fixture,"USD","1"),null,null),404);
        // Owners can archive stale references without depending on their former catalog visibility.
        input.put("version",1);input.put("status","ARCHIVED");data(call("PUT","/vendor/listings/"+id,fixture.token(),input));
    }

    @Test
    void offerAndVendorPaginationAreScopedAndFilterBound() throws Exception {
        var fixture=fixture();
        for (int i=0;i<3;i++) { data(call("POST","/vendor/listings?vendorId="+fixture.vendor(),fixture.token(),listing(fixture))); }
        var ids=new java.util.HashSet<String>();String cursor=null;
        do {
            var response=call("GET",offers(fixture,"USD","1")+"&pageSize=1"+(cursor==null?"":"&cursor="+encode(cursor)),null,null);
            var rows=data(response);assertThat(rows.size()).isEqualTo(1);assertThat(ids.add(rows.get(0).path("listingId").asString())).isTrue();
            var next=mapper.readTree(response.body()).path("pagination").path("nextCursor");cursor=next.isNull()?null:next.asString();
            if (cursor!=null) { problem(call("GET",offers(fixture,"USD","5")+"&cursor="+encode(cursor),null,null),400); }
        } while (cursor!=null);
        assertThat(ids).hasSize(3);
        var first=mapper.readTree(call("GET","/vendor/listings?vendorId="+fixture.vendor()+"&pageSize=1",fixture.token(),null).body());
        var second=data(call("GET","/vendor/listings?vendorId="+fixture.vendor()+"&pageSize=1&cursor="+encode(first.path("pagination").path("nextCursor").asString()),fixture.token(),null));
        assertThat(second.get(0).path("listingId")).isNotEqualTo(first.path("data").get(0).path("listingId"));
        problem(call("GET",offers(fixture,"USD","1")+"&pageSize=101",null,null),400);
        problem(call("GET","/vendor/locations?vendorId="+fixture.vendor()+"&cursor=bad",fixture.token(),null),400);
    }

    @Test
    void concurrentListingWritersHaveOneWinnerAndOnePriceReplacement() throws Exception {
        var fixture=fixture();var input=listing(fixture);
        String id=data(call("POST","/vendor/listings?vendorId="+fixture.vendor(),fixture.token(),input)).path("listingId").asString();
        input.put("version",0);input.put("prices",java.util.List.of(price("USD","1.000001",NOW,null)));
        try (var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            var a=executor.submit(() -> {start.await();return call("PUT","/vendor/listings/"+id,fixture.token(),input);});
            var b=executor.submit(() -> {start.await();return call("PUT","/vendor/listings/"+id,fixture.token(),input);});
            start.countDown();assertThat(java.util.List.of(a.get().statusCode(),b.get().statusCode())).containsExactlyInAnyOrder(200,409);
        }
        assertThat(data(call("GET","/vendor/listings/"+id,fixture.token(),null)).path("version").asLong()).isEqualTo(1);
        try (var db=com.fresveg.testing.database.DatabaseFixture.admin(POSTGRES)) {
            assertThat(com.fresveg.testing.database.DatabaseFixture.scalar(db,"SELECT count(*) FROM supply.vendor_listing_prices WHERE listing_id='"+id+"'"))
                    .isEqualTo("2");
        }
    }

    @Test
    void generatedOpenApiDescribesCompletedSupplyPhasesAndResolvesAllSchemas() throws Exception {
        var response=get("/v3/api-docs","openapi");assertThat(response.statusCode()).isEqualTo(200);
        var spec=mapper.readTree(response.body());assertThat(spec.path("openapi").asString()).startsWith("3.1");
        assertThat(spec.path("paths").size()).isEqualTo(17);
        assertThat(spec.path("paths").path("/api/v1/supply/vendor/listings").path("post").path("responses").has("201")).isTrue();
        assertThat(spec.path("paths").toString()).contains("/internal/v1/inventory/reservations").doesNotContain("carts","checkout");
        for (var reference:spec.findValues("$ref")) { assertThat(spec.at(reference.asString().substring(1)).isMissingNode()).isFalse(); }
        var yaml=get("/v3/api-docs.yaml","openapi");assertThat(yaml.statusCode()).isEqualTo(200);
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/supply-api.yaml"),yaml.body());
    }


    @Autowired com.fresveg.supply.infrastructure.config.InventoryExpiration expiration;

    @Test
    void inventoryReceiptAdjustmentAndLedgerReconcileExactly() throws Exception {
        var f=stock("10.000001");
        var bs=data(call("GET",inventoryPath(f)+"/batches",f.owner().token(),null));
        String batch=bs.get(0).path("batchId").asString();
        var adjusted=data(call("PUT",inventoryPath(f),f.owner().token(),java.util.Map.of("version",1,"batchId",batch,"quantityDelta",new BigDecimal("-0.000001"),"reason","Scale correction")));
        assertThat(adjusted.path("quantityOnHand").decimalValue()).isEqualByComparingTo("10");
        problem(call("PUT",inventoryPath(f),f.owner().token(),java.util.Map.of("version",1,"batchId",batch,"quantityDelta",-1,"reason","Stale")),409);
        var tx=data(call("GET",inventoryPath(f)+"/transactions",f.owner().token(),null));assertThat(tx.size()).isEqualTo(2);
        reconcile(f);
    }

    @Test
    void simultaneousLastStockReservationsCannotOversell() throws Exception {
        var f=stock("1");String token=serviceToken("test-commerce");
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(8)) {
            var gate=new java.util.concurrent.CountDownLatch(1);var attempts=new java.util.ArrayList<java.util.concurrent.Future<HttpResponse<String>>>();
            for(int n=0;n<8;n++) attempts.add(pool.submit(()->{gate.await();return call("POST","/internal/v1/inventory/reservations",token,reservation(f,"1"));}));
            gate.countDown();int successes=0;
            for(var attempt:attempts) { var response=attempt.get();if(response.statusCode()==200) successes++;else problem(response,409); }
            assertThat(successes).isEqualTo(1);
        }
        reconcile(f);
        assertThat(inventory(f).path("reservedQuantity").decimalValue()).isEqualByComparingTo("1");
    }

    @Test
    void reservationRetriesCommitAndReleaseAreIdempotentAndClientScoped() throws Exception {
        var f=stock("5");var request=reservation(f,"2");String token=serviceToken("test-commerce");
        var first=data(call("POST","/internal/v1/inventory/reservations",token,request));String id=first.path("reservationId").asString();
        assertThat(data(call("POST","/internal/v1/inventory/reservations",token,request)).path("reservationId").asString()).isEqualTo(id);
        var changed=new java.util.LinkedHashMap<>(request);changed.put("quantity",3);problem(call("POST","/internal/v1/inventory/reservations",token,changed),409);
        problem(call("POST","/internal/v1/inventory/reservations/"+id+"/commit",serviceToken("test-other"),null),404);
        for(int n=0;n<2;n++) assertThat(data(call("POST","/internal/v1/inventory/reservations/"+id+"/commit",token,null)).path("status").asString()).isEqualTo("COMMITTED");
        problem(call("POST","/internal/v1/inventory/reservations/"+id+"/release",token,null),409);
        var second=data(call("POST","/internal/v1/inventory/reservations",token,reservation(f,"3")));
        for(int n=0;n<2;n++) assertThat(data(call("POST","/internal/v1/inventory/reservations/"+second.path("reservationId").asString()+"/release",token,null)).path("status").asString()).isEqualTo("RELEASED");
        assertThat(inventory(f).path("quantityOnHand").decimalValue()).isEqualByComparingTo("3");
        assertThat(inventory(f).path("reservedQuantity").decimalValue()).isEqualByComparingTo("0");reconcile(f);
    }

    @Test
    void concurrentCommitAndReleaseProduceOneTerminalMovement() throws Exception {
        var f=stock("2");String token=serviceToken("test-commerce");String id=data(call("POST","/internal/v1/inventory/reservations",token,reservation(f,"2"))).path("reservationId").asString();
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var gate=new java.util.concurrent.CountDownLatch(1);
            var a=pool.submit(()->{gate.await();return call("POST","/internal/v1/inventory/reservations/"+id+"/commit",token,null);});
            var b=pool.submit(()->{gate.await();return call("POST","/internal/v1/inventory/reservations/"+id+"/release",token,null);});gate.countDown();
            assertThat(java.util.List.of(a.get().statusCode(),b.get().statusCode())).containsExactlyInAnyOrder(200,409);
        }
        assertThat(inventory(f).path("reservedQuantity").decimalValue()).isEqualByComparingTo("0");reconcile(f);
        assertThat(data(call("GET",inventoryPath(f)+"/transactions",f.owner().token(),null)).size()).isEqualTo(3);
    }

    @Test
    void automaticExpirationAndLateCommitReleaseStockExactlyOnce() throws Exception {
        var f=stock("4");String token=serviceToken("test-commerce");
        String id=data(call("POST","/internal/v1/inventory/reservations",token,reservation(f,"2"))).path("reservationId").asString();
        expireFixture(id);expiration.expire();expiration.expire();
        assertThat(data(call("POST","/internal/v1/inventory/reservations/"+id+"/commit",token,null)).path("status").asString()).isEqualTo("EXPIRED");
        String late=data(call("POST","/internal/v1/inventory/reservations",token,reservation(f,"4"))).path("reservationId").asString();
        expireFixture(late);
        assertThat(data(call("POST","/internal/v1/inventory/reservations/"+late+"/commit",token,null)).path("status").asString()).isEqualTo("EXPIRED");
        assertThat(inventory(f).path("quantityOnHand").decimalValue()).isEqualByComparingTo("4");
        assertThat(inventory(f).path("reservedQuantity").decimalValue()).isEqualByComparingTo("0");reconcile(f);
    }

    @Test
    void fefoAllocatesMultipleBatchesAndExcludesExpiredStock() throws Exception {
        var f=stock("2");var next=receipt("3",1);next.put("expiryDate","2026-09-10");next.put("batchNumber","EARLY");
        data(call("POST",inventoryPath(f)+"/batches",f.owner().token(),next));String token=serviceToken("test-commerce");
        String id=data(call("POST","/internal/v1/inventory/reservations",token,reservation(f,"4"))).path("reservationId").asString();
        data(call("POST","/internal/v1/inventory/reservations/"+id+"/commit",token,null));
        var bs=data(call("GET",inventoryPath(f)+"/batches",f.owner().token(),null));
        for(var b:bs) if(b.path("batchNumber").asString().equals("EARLY")) assertThat(b.path("quantityRemaining").decimalValue()).isEqualByComparingTo("0");
        try(var db=com.fresveg.testing.database.DatabaseFixture.admin(POSTGRES);var st=db.createStatement()) {
            st.execute("UPDATE supply.inventory_batches SET expiry_date='2026-09-08' WHERE inventory_id='"+f.id()+"'");
        }
        problem(call("POST","/internal/v1/inventory/reservations",token,reservation(f,"1")),409);reconcile(f);
    }

    @Test
    void inventoryAuthorizationAndInputCannotBypassReservedStock() throws Exception {
        var f=stock("3");var foreign=fixture();
        problem(call("GET",inventoryPath(f)+"/batches",foreign.token(),null),404);
        problem(call("POST",inventoryPath(f)+"/batches",foreign.token(),receipt("1",1)),404);
        String staff=TestIdentityProvider.token(UUID.randomUUID().toString());TestAccountServer.membership(staff,f.owner().vendor(),"VENDOR_STAFF");
        problem(call("POST",inventoryPath(f)+"/batches",staff,receipt("1",1)),403);
        problem(call("POST","/internal/v1/inventory/reservations",f.owner().token(),reservation(f,"1")),403);
        problem(call("POST","/internal/v1/inventory/reservations",null,reservation(f,"1")),401);
        problem(call("POST","/internal/v1/inventory/reservations",serviceToken("unconfigured"),reservation(f,"1")),403);
        problem(call("POST","/internal/v1/inventory/reservations",TestIdentityProvider.token("test-commerce"),reservation(f,"1")),403);
        String batch=data(call("GET",inventoryPath(f)+"/batches",f.owner().token(),null)).get(0).path("batchId").asString();
        data(call("POST","/internal/v1/inventory/reservations",serviceToken("test-commerce"),reservation(f,"3")));
        problem(call("POST",inventoryPath(f)+"/adjustments",f.owner().token(),java.util.Map.of("version",2,"batchId",batch,"quantityDelta",-1,"reason","Would consume reserved stock")),409);
        var invalid=reservation(f,"0");problem(call("POST","/internal/v1/inventory/reservations",serviceToken("test-commerce"),invalid),400);
        invalid=reservation(f,"1");invalid.put("expiresAt",NOW.plusSeconds(3601).toString());problem(call("POST","/internal/v1/inventory/reservations",serviceToken("test-commerce"),invalid),400);reconcile(f);
    }

    @Test
    void inventoryPaginationIsScopedAndFailedReceiptRollsBack() throws Exception {
        var f=stock("1");var duplicate=receipt("1",1);duplicate.put("batchNumber","INITIAL");
        problem(call("POST",inventoryPath(f)+"/batches",f.owner().token(),duplicate),409);
        assertThat(inventory(f).path("version").asLong()).isEqualTo(1);reconcile(f);
        data(call("POST",inventoryPath(f)+"/batches",f.owner().token(),receipt("1",1)));
        var first=mapper.readTree(call("GET",inventoryPath(f)+"/batches?pageSize=1",f.owner().token(),null).body());
        String cursor=first.path("pagination").path("nextCursor").asString();
        assertThat(data(call("GET",inventoryPath(f)+"/batches?pageSize=1&cursor="+encode(cursor),f.owner().token(),null)).size()).isEqualTo(1);
        problem(call("GET",inventoryPath(f)+"/transactions?cursor="+encode(cursor),f.owner().token(),null),400);
    }

    @Test
    void publicAvailabilityExcludesReservedStockAndBatchesExpiringBeforeDelivery() throws Exception {
        var f=stock("2");String listing=data(call("GET","/vendor/inventory?vendorId="+f.owner().vendor(),f.owner().token(),null)).get(0).path("listingId").asString();
        String path="/listings/"+listing+"/availability?quantity=2&requiredUntil="+encode(NOW.plusSeconds(300).toString());
        assertThat(data(call("GET",path,null,null)).path("available").asBoolean()).isTrue();
        int movements=data(call("GET",inventoryPath(f)+"/transactions",f.owner().token(),null)).size();
        data(call("POST","/internal/v1/inventory/reservations",serviceToken("test-commerce"),reservation(f,"1")));
        assertThat(data(call("GET",path,null,null)).path("available").asBoolean()).isFalse();
        assertThat(data(call("GET","/listings/"+listing+"/availability?quantity=1&requiredUntil="+encode("2026-09-13T00:00:00Z"),null,null)).path("available").asBoolean()).isFalse();
        assertThat(data(call("GET",inventoryPath(f)+"/transactions",f.owner().token(),null)).size()).isEqualTo(movements+1);reconcile(f);
    }
    @Test
    void availabilityIsReadOnlyAndValidatesHorizonAndListingVisibility() throws Exception {
        var owner=fixture();String listing=data(call("POST","/vendor/listings?vendorId="+owner.vendor(),owner.token(),listing(owner))).path("listingId").asString();
        String path="/listings/"+listing+"/availability?quantity=1&requiredUntil="+encode(NOW.plusSeconds(300).toString());
        assertThat(data(call("GET",path,null,null)).path("available").asBoolean()).isFalse();
        assertThat(data(call("GET","/vendor/inventory?vendorId="+owner.vendor(),owner.token(),null)).size()).isZero();
        problem(call("GET","/listings/"+listing+"/availability?quantity=0&requiredUntil="+encode(NOW.plusSeconds(300).toString()),null,null),400);
        problem(call("GET","/listings/"+listing+"/availability?quantity=1&requiredUntil="+encode(NOW.minusSeconds(1).toString()),null,null),400);
        problem(call("GET","/listings/"+UUID.randomUUID()+"/availability?quantity=1&requiredUntil="+encode(NOW.plusSeconds(300).toString()),null,null),404);
    }

    @Test void orderReservationsEnforceServiceOwnershipAndDeliveryBatchHorizon()throws Exception {
        var f=stock("2");String listing=inventory(f).path("listingId").asString(),token=serviceToken("test-commerce"),ref=UUID.randomUUID().toString();
        var request=new java.util.LinkedHashMap<String,Object>(java.util.Map.of("listingId",listing,"externalReference",ref,"quantity",1,"expiresAt",NOW.plusSeconds(300).toString(),"requiredUntil","2026-09-12T00:00:00Z"));
        problem(call("POST","/internal/v1/inventory/order-reservations",f.owner().token(),request),403);
        request.put("requiredUntil","2026-09-12T00:00:01Z");problem(call("POST","/internal/v1/inventory/order-reservations",token,request),409);
        request.put("requiredUntil","2026-09-12T00:00:00Z");var r=data(call("POST","/internal/v1/inventory/order-reservations",token,request));String id=r.path("reservationId").asString();
        assertThat(data(call("POST","/internal/v1/inventory/order-reservations",token,request)).path("reservationId").asString()).isEqualTo(id);
        assertThat(data(call("GET","/internal/v1/inventory/reservations/by-reference?externalReference="+ref,token,null)).path("reservationId").asString()).isEqualTo(id);
        problem(call("GET","/internal/v1/inventory/reservations/by-reference?externalReference="+ref,serviceToken("test-other"),null),404);problem(call("GET","/internal/v1/inventory/reservations/by-reference?externalReference="+ref,f.owner().token(),null),403);
        data(call("POST","/internal/v1/inventory/reservations/"+id+"/release",token,null));reconcile(f);
    }

    private record Stock(Fixture owner,String id) { }
    private Stock stock(String quantity) throws Exception {
        var f=fixture();String listingId=data(call("POST","/vendor/listings?vendorId="+f.vendor(),f.token(),listing(f))).path("listingId").asString();
        String id=data(call("POST","/vendor/inventory",f.token(),java.util.Map.of("listingId",listingId))).path("inventoryId").asString();
        var receipt=receipt(quantity,0);receipt.put("batchNumber","INITIAL");data(call("POST","/vendor/inventory/"+id+"/batches",f.token(),receipt));return new Stock(f,id);
    }
    private static String inventoryPath(Stock f) { return "/vendor/inventory/"+f.id(); }
    private tools.jackson.databind.JsonNode inventory(Stock f) throws Exception { return data(call("GET","/vendor/inventory?vendorId="+f.owner().vendor(),f.owner().token(),null)).get(0); }
    private static java.util.Map<String,Object> receipt(String quantity,long version) {
        var b=new java.util.LinkedHashMap<String,Object>();b.put("version",version);b.put("batchNumber",code());b.put("receivedDate","2026-09-08");b.put("expiryDate","2026-09-12");b.put("origin","Local farm");b.put("grade","A");b.put("certificationData",java.util.Map.of());b.put("quantity",new BigDecimal(quantity));return b;
    }
    private static java.util.Map<String,Object> reservation(Stock f,String quantity) { return new java.util.LinkedHashMap<>(java.util.Map.of("inventoryId",f.id(),"externalReference",UUID.randomUUID().toString(),"quantity",new BigDecimal(quantity),"expiresAt",NOW.plusSeconds(300).toString())); }
    private static String serviceToken(String subject) throws Exception { return TestIdentityProvider.token(subject,c->c.claim("scope","inventory.reserve"),false); }
    private void expireFixture(String id) throws Exception {
        try(var db=com.fresveg.testing.database.DatabaseFixture.admin(POSTGRES);var st=db.createStatement()) { st.execute("UPDATE supply.inventory_reservations SET expires_at='2026-09-08T12:00:00Z' WHERE reservation_id='"+id+"'"); }
    }
    private void reconcile(Stock f) throws Exception {
        try(var db=com.fresveg.testing.database.DatabaseFixture.admin(POSTGRES)) {
            String sql="SELECT (i.quantity_on_hand=(SELECT sum(quantity_delta) FROM supply.inventory_transactions WHERE inventory_id=i.inventory_id) AND i.reserved_quantity=(SELECT sum(reserved_delta) FROM supply.inventory_transactions WHERE inventory_id=i.inventory_id) AND i.quantity_on_hand=(SELECT sum(quantity_remaining) FROM supply.inventory_batches WHERE inventory_id=i.inventory_id) AND i.reserved_quantity=(SELECT sum(reserved_quantity) FROM supply.inventory_batches WHERE inventory_id=i.inventory_id))::text FROM supply.inventory i WHERE inventory_id='"+f.id()+"'";
            assertThat(com.fresveg.testing.database.DatabaseFixture.scalar(db,sql)).isEqualTo("true");
        }
    }

    private Fixture fixture() throws Exception {
        String token=TestIdentityProvider.token(UUID.randomUUID().toString());UUID vendor=TestAccountServer.vendor(token,"VENDOR_ADMIN");
        var product=TestCatalogServer.product();
        String location=data(call("POST","/vendor/locations?vendorId="+vendor,token,location())).path("locationId").asString();
        return new Fixture(token,vendor,location,product);
    }
    private record Fixture(String token,UUID vendor,String location,tools.jackson.databind.JsonNode product) { }
    private static String code() { return "S"+UUID.randomUUID().toString().replace("-","").toUpperCase(java.util.Locale.ROOT); }
    private static java.util.Map<String,Object> location() {
        return new java.util.LinkedHashMap<>(java.util.Map.of("code",code(),"name","Farm","line1","1 Farm Road","city","Boston","postalCode","02110","countryCode","US","status","ACTIVE"));
    }
    private static java.util.Map<String,Object> listing(Fixture fixture) {
        var input=new java.util.LinkedHashMap<String,Object>();input.put("locationId",fixture.location());input.put("productId",fixture.product().path("productId").asString());
        input.put("variantId",fixture.product().path("variants").get(0).path("variantId").asString());input.put("vendorSku",code());input.put("uomCode","KG");
        input.put("minimumOrderQuantity",BigDecimal.ONE);input.put("status","ACTIVE");input.put("attributes",java.util.Map.of("grade","A"));input.put("prices",java.util.List.of(price("USD","2.750001",NOW,null)));return input;
    }
    private static java.util.Map<String,Object> price(String currency,String amount,Instant from,Instant to) {
        var input=new java.util.LinkedHashMap<String,Object>();input.put("currency",currency);input.put("unitPrice",new BigDecimal(amount));input.put("minQuantity",BigDecimal.ONE);
        input.put("validFrom",from.toString());input.put("validTo",to==null?null:to.toString());input.put("tiers",java.util.List.of());return input;
    }
    private static java.util.Map<String,Object> tier(String minimum,String amount) { return java.util.Map.of("minQuantity",new BigDecimal(minimum),"unitPrice",new BigDecimal(amount)); }
    private static String offers(Fixture fixture,String currency,String quantity) { return "/products/"+fixture.product().path("productId").asString()+"/offers?currency="+currency+"&quantity="+quantity; }
    private HttpResponse<String> call(String method,String path,String token,Object body) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+(path.startsWith("/internal/")?"":"/api/v1/supply")+path))
                .timeout(Duration.ofSeconds(20)).header("X-Correlation-ID","supply-test");
        if (token!=null) { request.header("Authorization","Bearer "+token); }
        if (body!=null) { request.header("Content-Type","application/json"); }
        request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        try (var client=HttpClient.newHttpClient()) { return client.send(request.build(),HttpResponse.BodyHandlers.ofString()); }
    }
    private tools.jackson.databind.JsonNode data(HttpResponse<String> response) {
        assertThat(response.statusCode()).withFailMessage(response.body()).isIn(200,201);
        var json=mapper.readTree(response.body());assertThat(json.path("meta").path("requestId").asString()).isEqualTo("supply-test");return json.path("data");
    }
    private void problem(HttpResponse<String> response,int status) {
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("content-type")).contains("application/problem+json");
        var json=mapper.readTree(response.body());assertThat(json.path("status").asInt()).isEqualTo(status);assertThat(json.path("correlationId").asString()).isEqualTo("supply-test");
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
