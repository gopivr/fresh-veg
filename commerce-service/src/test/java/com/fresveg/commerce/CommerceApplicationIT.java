package com.fresveg.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import com.fresveg.common.http.CorrelationIds;
import com.fresveg.commerce.application.outbox.OutboxPublicationService;
import com.fresveg.commerce.application.outbox.OutboxRepository;
import com.fresveg.commerce.infrastructure.outbox.LocalOutboxEventPublisher;
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
class CommerceApplicationIT extends ServiceDatabaseTest {
    private static final UUID DELIVERY_SLOT=UUID.randomUUID();
    private static final UUID DECLINED_PAYMENT=UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final Instant DELIVERY_START=Instant.now().plusSeconds(86400).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    private static final Instant DELIVERY_END=DELIVERY_START.plusSeconds(7200);

    @org.springframework.test.context.DynamicPropertySource
    static void owners(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("commerce.checkout.pricing-rules",()->"[{\"policyId\":\"test-only\",\"countryCode\":\"US\",\"currency\":\"USD\",\"discountRate\":0.1,\"taxRate\":0.1,\"taxDelivery\":false}]");
        registry.add("commerce.checkout.delivery-slots",()->new JsonMapper().writeValueAsString(java.util.List.of(java.util.Map.of("deliverySlotId",DELIVERY_SLOT,"startsAt",DELIVERY_START,"endsAt",DELIVERY_END,"countryCode","US","postalCodes",java.util.List.of("02110"),"currency","USD","deliveryFee",new BigDecimal("2.50")))));
        registry.add("commerce.security.issuer-uri",()->TestIdentityProvider.ISSUER);
        registry.add("commerce.security.audience",()->"fresveg-commerce");
        registry.add("commerce.security.jwk-set-uri",TestIdentityProvider::jwksUri);
        registry.add("commerce.account-base-url",TestAccountServer::url);
        registry.add("commerce.supply-base-url",TestSupplyProxy::url);
        registry.add("commerce.orders.catalog-base-url",TestCatalogServer::url);
        registry.add("commerce.orders.inventory-token-file",TestSupplyServer::tokenFile);
        registry.add("commerce.payments.local.declined-method-ids",()->DECLINED_PAYMENT.toString());
        registry.add("commerce.outbox.publisher",()->"local");
        registry.add("commerce.outbox.scheduler-enabled",()->"false");
        registry.add("commerce.outbox.local.fail-event-types",()->"ShipmentDelivered");
    }
    @org.junit.jupiter.api.AfterAll static void stopOwners() { TestSupplyProxy.stop();TestSupplyServer.stop();TestCatalogServer.stop();TestAccountServer.stop(); }

    @LocalServerPort
    int port;
    @Autowired
    JsonMapper mapper;
    @Autowired
    Environment environment;
    @Autowired
    MeterRegistry meterRegistry;
    @Autowired
    OutboxPublicationService outboxPublication;
    @Autowired
    OutboxRepository outboxRepository;
    @Autowired
    LocalOutboxEventPublisher localOutbox;

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
        assertThat(meterRegistry.find("fresveg.orders.created").counter()).isNotNull();
        assertThat(meterRegistry.find("fresveg.checkout.failures").counter()).isNotNull();
        assertThat(meterRegistry.find("fresveg.payment.failures").counter()).isNotNull();
        assertThat(meterRegistry.find("fresveg.outbox.backlog").gauge()).isNotNull();
        var response = get("/actuator/prometheus", "metrics-probe");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue(CorrelationIds.HEADER)).contains("metrics-probe");
        assertThat(response.body()).contains("http_server_requests", "fresveg_outbox_backlog", "application=\"" + environment.getProperty("spring.application.name") + "\"");
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
        assertThat(environment.getProperty("spring.application.name")).isEqualTo("commerce-service");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(environment.getProperty("spring.sql.init.mode")).isEqualTo("never");
    }


    @Test void cartLifecycleUsesCurrentSupplyPricesAndNeverPersistsThem() throws Exception {
        String token=token();var f=listing();String cart=create(token);var added=data(call("POST","/"+cart+"/items",token,java.util.Map.of("version",0,"listingId",f.id(),"quantity",5)));
        String item=added.path("itemId").asString();
        var read=call("GET","/"+cart,token,null);assertThat(read.body()).contains("\"unitPrice\":2.125001");
        var changed=price("9000000000000.123456");f.input().put("version",0);f.input().put("prices",java.util.List.of(changed));TestSupplyServer.mutate("PUT","/vendor/listings/"+f.id(),f.vendorToken(),f.input());
        assertThat(call("GET","/"+cart,token,null).body()).contains("\"unitPrice\":9000000000000.123456");
        assertThat(data(call("PATCH","/"+cart+"/items/"+item,token,java.util.Map.of("version",1,"quantity",2))).path("version").asLong()).isEqualTo(2);
        assertThat(data(call("DELETE","/"+cart+"/items/"+item+"?version=2",token,null)).path("version").asLong()).isEqualTo(3);
        assertThat(data(call("GET","/"+cart,token,null)).path("items").size()).isZero();
        try(var db=com.fresveg.testing.database.DatabaseFixture.admin(POSTGRES)) {
            assertThat(com.fresveg.testing.database.DatabaseFixture.scalar(db,"SELECT count(*) FROM information_schema.columns WHERE table_schema='commerce' AND table_name IN ('carts','cart_items') AND (column_name LIKE '%price%' OR column_name LIKE '%total%')")).isEqualTo("0");
            assertThat(com.fresveg.testing.database.DatabaseFixture.scalar(db,"SELECT created_by::text FROM commerce.carts WHERE cart_id='"+cart+"'")).isEqualTo(TestAccountServer.user(token,false));
        }
    }
    @Test void cartAndItemOwnershipIgnoreClientClaimsAndHideForeignResources() throws Exception {
        String a=token(),b=token();String cart=create(a);String other=create(a);var f=listing();
        String item=data(call("POST","/"+cart+"/items",a,java.util.Map.of("version",0,"listingId",f.id(),"quantity",1))).path("itemId").asString();
        for(String method:java.util.List.of("GET","POST")) problem(call(method,"/"+cart+(method.equals("POST")?"/items":""),b,method.equals("POST")?java.util.Map.of("version",1,"listingId",f.id(),"quantity",1):null),404);
        problem(call("PATCH","/"+cart+"/items/"+item,b,java.util.Map.of("version",1,"quantity",2)),404);
        problem(call("DELETE","/"+cart+"/items/"+item+"?version=1",b,null),404);
        problem(call("PATCH","/"+other+"/items/"+item,a,java.util.Map.of("version",0,"quantity",2)),404);
        var injected=new java.util.LinkedHashMap<String,Object>();injected.put("currency","USD");injected.put("customerId",UUID.randomUUID());problem(call("POST","",a,injected),400);
        problem(call("POST","/"+cart+"/items",a,java.util.Map.of("version",1,"listingId",f.id(),"quantity",1,"unitPrice",1)),400);
    }
    @Test void quantityValidationDuplicateRollbackAndStaleVersionProtectCart() throws Exception {
        String t=token(),cart=create(t);var f=listing();var add=java.util.Map.of("version",0,"listingId",f.id(),"quantity",1);
        String item=data(call("POST","/"+cart+"/items",t,add)).path("itemId").asString();
        problem(call("POST","/"+cart+"/items",t,java.util.Map.of("version",1,"listingId",f.id(),"quantity",2)),409);
        assertThat(data(call("GET","/"+cart,t,null)).path("version").asLong()).isEqualTo(1);
        problem(call("PATCH","/"+cart+"/items/"+item,t,java.util.Map.of("version",0,"quantity",2)),409);
        for(var quantity:java.util.List.of(BigDecimal.ZERO,new BigDecimal("-1"),new BigDecimal("0.0000001"))) problem(call("PATCH","/"+cart+"/items/"+item,t,java.util.Map.of("version",1,"quantity",quantity)),400);
        problem(call("POST","/"+cart+"/items",t,java.util.Map.of("version",1,"listingId",UUID.randomUUID(),"quantity",1)),409);
        problem(call("POST","",t,java.util.Map.of("currency","XXX")),400);
        problem(call("DELETE","/"+cart+"/items/"+item+"?version=-1",t,null),400);
    }
    @Test void unavailableListingIsVisibleWithoutStalePriceAndCanBeRemoved() throws Exception {
        String t=token(),cart=create(t);var f=listing();String item=data(call("POST","/"+cart+"/items",t,java.util.Map.of("version",0,"listingId",f.id(),"quantity",1))).path("itemId").asString();
        f.input().put("version",0);f.input().put("status","ARCHIVED");TestSupplyServer.mutate("PUT","/vendor/listings/"+f.id(),f.vendorToken(),f.input());
        var response=data(call("GET","/"+cart,t,null)).path("items").get(0);assertThat(response.path("pricingStatus").asString()).isEqualTo("UNAVAILABLE");assertThat(response.path("currentPrice").isNull()).isTrue();
        data(call("DELETE","/"+cart+"/items/"+item+"?version=1",t,null));
    }
    @Test void supplyOutageFailsClosedWithoutMutatingCartAndRemovalStillWorks() throws Exception {
        String t=token(),cart=create(t);var f=listing();String item=data(call("POST","/"+cart+"/items",t,java.util.Map.of("version",0,"listingId",f.id(),"quantity",1))).path("itemId").asString();
        TestSupplyProxy.unavailable=true;
        try {
            problem(call("GET","/"+cart,t,null),503);
            problem(call("PATCH","/"+cart+"/items/"+item,t,java.util.Map.of("version",1,"quantity",2)),503);
            assertThat(data(call("DELETE","/"+cart+"/items/"+item+"?version=1",t,null)).path("version").asLong()).isEqualTo(2);
        } finally { TestSupplyProxy.unavailable=false; }
        assertThat(data(call("GET","/"+cart,t,null)).path("items").size()).isZero();
    }
    @Test void simultaneousQuantityEditsHaveOneWinner() throws Exception {
        String t=token(),cart=create(t);var f=listing();String item=data(call("POST","/"+cart+"/items",t,java.util.Map.of("version",0,"listingId",f.id(),"quantity",1))).path("itemId").asString();
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var gate=new java.util.concurrent.CountDownLatch(1);var a=pool.submit(()->{gate.await();return call("PATCH","/"+cart+"/items/"+item,t,java.util.Map.of("version",1,"quantity",2));});var b=pool.submit(()->{gate.await();return call("PATCH","/"+cart+"/items/"+item,t,java.util.Map.of("version",1,"quantity",3));});gate.countDown();
            assertThat(java.util.List.of(a.get().statusCode(),b.get().statusCode())).containsExactlyInAnyOrder(200,409);
        }
        assertThat(data(call("GET","/"+cart,t,null)).path("version").asLong()).isEqualTo(2);
    }
    @Test void itemPaginationBindsCustomerCartAndVersion() throws Exception {
        String t=token(),cart=create(t);for(int i=0;i<2;i++){var f=listing();data(call("POST","/"+cart+"/items",t,java.util.Map.of("version",i,"listingId",f.id(),"quantity",1)));}
        var first=data(call("GET","/"+cart+"?pageSize=1",t,null));String cursor=first.path("pagination").path("nextCursor").asString();
        var second=data(call("GET","/"+cart+"?pageSize=1&cursor="+cursor,t,null));assertThat(second.path("items").size()).isEqualTo(1);assertThat(second.path("items").get(0).path("itemId")).isNotEqualTo(first.path("items").get(0).path("itemId"));
        String item=first.path("items").get(0).path("itemId").asString();data(call("DELETE","/"+cart+"/items/"+item+"?version=2",t,null));problem(call("GET","/"+cart+"?cursor="+cursor,t,null),400);
        problem(call("GET","/"+cart+"?pageSize=11",t,null),400);
    }
    @Test void cartJwtValidationAndOpenApiAreComplete() throws Exception {
        problem(call("POST","",null,java.util.Map.of("currency","USD")),401);
        for(String bad:java.util.List.of("malformed",TestIdentityProvider.token("bad",c->c.audience("wrong"),false),TestIdentityProvider.token("bad",c->{},true),TestIdentityProvider.token("bad",c->c.expirationTime(java.util.Date.from(Instant.now().minusSeconds(300))),false))) problem(call("POST","",bad,java.util.Map.of("currency","USD")),401);
        var spec=mapper.readTree(get("/v3/api-docs","cart-test").body());assertThat(spec.path("paths").size()).isEqualTo(12);assertThat(spec.path("paths").toString()).contains("/api/v1/checkout/preview","/api/v1/orders").doesNotContain("/internal/");
        for(var ref:spec.findValues("$ref"))assertThat(spec.at(ref.asString().substring(1)).isMissingNode()).isFalse();
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/commerce-api.yaml"),get("/v3/api-docs.yaml","cart-test").body());
    }

    @Test void checkoutPreviewReconcilesCurrentPricesAndNeverWritesCommerceOrReservations()throws Exception {
        String t=token(),cart=create(t);var f=listing();stock(f,"10");data(call("POST","/"+cart+"/items",t,java.util.Map.of("version",0,"listingId",f.id(),"quantity",5)));
        UUID address=address(t,"02110");String before=commerceState(cart);long reservations=TestSupplyServer.reservations();
        var preview=data(call("POST","/api/v1/checkout/preview",t,preview(cart,address,DELIVERY_SLOT)));
        assertThat(preview.path("subtotal").decimalValue()).isEqualByComparingTo("10.63");assertThat(preview.path("discount").decimalValue()).isEqualByComparingTo("1.06");assertThat(preview.path("tax").decimalValue()).isEqualByComparingTo("0.96");assertThat(preview.path("deliveryFee").decimalValue()).isEqualByComparingTo("2.50");assertThat(preview.path("grandTotal").decimalValue()).isEqualByComparingTo("13.03");assertThat(preview.path("stockReserved").asBoolean()).isFalse();assertThat(preview.path("deliveryAddress").path("addressId").asString()).isEqualTo(address.toString());
        assertThat(commerceState(cart)).isEqualTo(before);assertThat(TestSupplyServer.reservations()).isEqualTo(reservations);
        f.input().put("version",0);f.input().put("prices",java.util.List.of(price("3.00")));TestSupplyServer.mutate("PUT","/vendor/listings/"+f.id(),f.vendorToken(),f.input());
        assertThat(data(call("POST","/api/v1/checkout/preview",t,preview(cart,address,DELIVERY_SLOT))).path("subtotal").decimalValue()).isEqualByComparingTo("15.00");
    }
    @Test void checkoutChecksCartAndAddressOwnershipAndRejectsCallerAmounts()throws Exception {
        String a=token(),b=token(),cart=create(a);var f=listing();stock(f,"2");data(call("POST","/"+cart+"/items",a,java.util.Map.of("version",0,"listingId",f.id(),"quantity",1)));
        UUID own=address(a,"02110"),foreign=address(b,"02110");problem(call("POST","/api/v1/checkout/preview",b,preview(cart,foreign,DELIVERY_SLOT)),404);problem(call("POST","/api/v1/checkout/preview",a,preview(cart,foreign,DELIVERY_SLOT)),404);
        var injected=new java.util.LinkedHashMap<>(preview(cart,own,DELIVERY_SLOT));injected.put("grandTotal",0);problem(call("POST","/api/v1/checkout/preview",a,injected),400);
        problem(call("POST","/api/v1/checkout/preview",null,preview(cart,own,DELIVERY_SLOT)),401);
        problem(call("POST","/api/v1/checkout/preview",a,java.util.Map.of("cartId",cart)),400);
    }
    @Test void checkoutRejectsEmptyUnavailableStockAndInvalidDeliveryWithoutMutation()throws Exception {
        String t=token(),cart=create(t);UUID addr=address(t,"02110");problem(call("POST","/api/v1/checkout/preview",t,preview(cart,addr,DELIVERY_SLOT)),409);
        var f=listing();data(call("POST","/"+cart+"/items",t,java.util.Map.of("version",0,"listingId",f.id(),"quantity",2)));String before=commerceState(cart);
        problem(call("POST","/api/v1/checkout/preview",t,preview(cart,addr,DELIVERY_SLOT)),409);stock(f,"1");problem(call("POST","/api/v1/checkout/preview",t,preview(cart,addr,DELIVERY_SLOT)),409);
        problem(call("POST","/api/v1/checkout/preview",t,preview(cart,addr,UUID.randomUUID())),409);problem(call("POST","/api/v1/checkout/preview",t,preview(cart,address(t,"99999"),DELIVERY_SLOT)),409);assertThat(commerceState(cart)).isEqualTo(before);
    }
    @Test void checkoutSupplyOutageFailsClosedAndRecoveryUsesLiveData()throws Exception {
        String t=token(),cart=create(t);var f=listing();stock(f,"2");data(call("POST","/"+cart+"/items",t,java.util.Map.of("version",0,"listingId",f.id(),"quantity",1)));UUID addr=address(t,"02110");
        String before=commerceState(cart);TestSupplyProxy.unavailable=true;
        try {problem(call("POST","/api/v1/checkout/preview",t,preview(cart,addr,DELIVERY_SLOT)),503);}finally{TestSupplyProxy.unavailable=false;}
        assertThat(data(call("POST","/api/v1/checkout/preview",t,preview(cart,addr,DELIVERY_SLOT))).path("items").size()).isEqualTo(1);assertThat(commerceState(cart)).isEqualTo(before);
    }
    @Test void checkoutIncludesItemsBeyondTheCartReadPage()throws Exception {
        String t=token(),cart=create(t);for(int i=0;i<11;i++){var f=listing();stock(f,"1");data(call("POST","/"+cart+"/items",t,java.util.Map.of("version",i,"listingId",f.id(),"quantity",1)));}
        var result=data(call("POST","/api/v1/checkout/preview",t,preview(cart,address(t,"02110"),DELIVERY_SLOT)));assertThat(result.path("items").size()).isEqualTo(11);assertThat(result.path("subtotal").decimalValue()).isEqualByComparingTo("30.25");
    }
    @Autowired com.fresveg.commerce.application.order.OrderService orderService;
    @Autowired com.fresveg.commerce.infrastructure.persistence.OrderRepository orderRepository;

    @Test void orderIsDurableIdempotentAndSnapshotsSurviveMutablePriceAndAddress()throws Exception {
        String t=token(),cart=create(t);var f=listing();stock(f,"10");data(call("POST","/"+cart+"/items",t,java.util.Map.of("version",0,"listingId",f.id(),"quantity",5)));
        UUID addr=address(t,"02110");var body=preview(cart,addr,DELIVERY_SLOT);String key=UUID.randomUUID().toString();long before=TestSupplyServer.activeReservations();
        var created=data(order(t,key,body));String id=created.path("orderId").asString();assertThat(created.path("status").asString()).isEqualTo("CONFIRMED");assertThat(created.path("grandTotal").decimalValue()).isEqualByComparingTo("13.03");assertThat(created.path("items").get(0).path("productName").asString()).isNotBlank();assertThat(created.path("items").get(0).path("vendorName").asString()).isNotBlank();assertThat(TestSupplyServer.activeReservations()).isEqualTo(before);
        assertThat(data(order(t,key,body))).isEqualTo(created);assertThat(TestSupplyServer.activeReservations()).isEqualTo(before);
        problem(order(t,key,preview(cart,UUID.randomUUID(),DELIVERY_SLOT)),409);
        f.input().put("version",0);f.input().put("prices",java.util.List.of(price("3.00")));TestSupplyServer.mutate("PUT","/vendor/listings/"+f.id(),f.vendorToken(),f.input());
        try(var client=HttpClient.newHttpClient()) {var r=client.send(HttpRequest.newBuilder(URI.create(TestAccountServer.url()+"/api/v1/accounts/me/addresses/"+addr)).header("Authorization","Bearer "+t).header("Content-Type","application/json").PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(java.util.Map.of("version",0,"recipientName","Changed","line1","99 Changed","city","Boston","postalCode","02110","countryCode","US")))).build(),HttpResponse.BodyHandlers.ofString());assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(200);}
        assertThat(data(call("GET","/api/v1/orders/"+id,t,null))).isEqualTo(created);
        try(var db=com.fresveg.testing.database.DatabaseFixture.admin(POSTGRES)) {assertThat(com.fresveg.testing.database.DatabaseFixture.scalar(db,"SELECT count(*) FROM commerce.outbox_events WHERE aggregate_id='"+id+"' AND event_type IN ('OrderCreated','PaymentAuthorized','OrderConfirmed')")).isEqualTo("3");assertThat(com.fresveg.testing.database.DatabaseFixture.scalar(db,"SELECT count(*) FROM commerce.payment_attempts WHERE order_id='"+id+"' AND status='AUTHORIZED'")).isEqualTo("1");}
        assertThat(data(call("POST","/api/v1/orders/"+id+"/cancel",t,null)).path("status").asString()).isEqualTo("CANCELLED");
        assertThat(data(call("POST","/api/v1/orders/"+id+"/cancel",t,null)).path("version").asLong()).isEqualTo(3);
        assertThat(TestSupplyServer.activeReservations()).isEqualTo(before);assertThat(data(order(t,key,body))).isEqualTo(created);
        problem(order(t,UUID.randomUUID().toString(),body),409);assertThat(TestSupplyServer.activeReservations()).isEqualTo(before);
    }
    @Test void orderOwnershipInputAndPaginationAreEnforced()throws Exception {
        String t=token(),other=token(),cart=create(t);var f=listing();stock(f,"3");data(call("POST","/"+cart+"/items",t,java.util.Map.of("version",0,"listingId",f.id(),"quantity",1)));var body=preview(cart,address(t,"02110"),DELIVERY_SLOT);
        problem(order(null,"key",body),401);problem(order(t,null,body),400);problem(order(t,"bad key",body),400);
        var injected=new java.util.LinkedHashMap<>(body);injected.put("grandTotal",0);problem(order(t,"key",injected),400);problem(order(other,"key",body),404);
        var a=data(order(t,"key",body));String id=a.path("orderId").asString();problem(call("GET","/api/v1/orders/"+id,other,null),404);problem(call("POST","/api/v1/orders/"+id+"/cancel",other,null),404);
        assertThat(data(call("GET","/api/v1/orders",other,null)).path("data").size()).isZero();problem(call("GET","/api/v1/orders?cursor="+id,other,null),404);problem(call("GET","/api/v1/orders?pageSize=11",t,null),400);
        String second=create(t);data(call("POST","/"+second+"/items",t,java.util.Map.of("version",0,"listingId",f.id(),"quantity",1)));var b=data(order(t,"key2",preview(second,address(t,"02110"),DELIVERY_SLOT)));
        var page=data(call("GET","/api/v1/orders?pageSize=1",t,null));assertThat(page.path("hasNext").asBoolean()).isTrue();var next=data(call("GET","/api/v1/orders?pageSize=1&cursor="+page.path("nextCursor").asString(),t,null));assertThat(next.path("data").get(0).path("orderId")).isNotEqualTo(page.path("data").get(0).path("orderId"));assertThat(next.path("hasNext").asBoolean()).isFalse();
        data(call("POST","/api/v1/orders/"+id+"/cancel",t,null));data(call("POST","/api/v1/orders/"+b.path("orderId").asString()+"/cancel",t,null));
    }
    @Test void concurrentDuplicateKeyCreatesExactlyOneOrderAndHold()throws Exception {
        String t=token(),cart=create(t);var f=listing();stock(f,"2");data(call("POST","/"+cart+"/items",t,java.util.Map.of("version",0,"listingId",f.id(),"quantity",1)));var body=preview(cart,address(t,"02110"),DELIVERY_SLOT);String key=UUID.randomUUID().toString();long before=TestSupplyServer.activeReservations();
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {var gate=new java.util.concurrent.CountDownLatch(1);var a=pool.submit(()->{gate.await();return order(t,key,body);});var b=pool.submit(()->{gate.await();return order(t,key,body);});gate.countDown();var ar=a.get();var br=b.get();assertThat(ar.statusCode()).isIn(201,409);assertThat(br.statusCode()).isIn(201,409);assertThat(java.util.List.of(ar.statusCode(),br.statusCode())).contains(201);}
        var result=data(order(t,key,body));assertThat(TestSupplyServer.activeReservations()).isEqualTo(before);data(call("POST","/api/v1/orders/"+result.path("orderId").asString()+"/cancel",t,null));
    }
    @Test void partialMultiVendorFailureAndLostReservationResponseAreCompensated()throws Exception {
        String t=token(),cart=create(t);for(int n=0;n<2;n++){var f=listing();stock(f,"1");data(call("POST","/"+cart+"/items",t,java.util.Map.of("version",n,"listingId",f.id(),"quantity",1)));}var body=preview(cart,address(t,"02110"),DELIVERY_SLOT);long before=TestSupplyServer.activeReservations();
        TestSupplyProxy.reserveCalls.set(0);TestSupplyProxy.rejectReservationNumber=2;
        try{problem(order(t,"partial",body),409);}finally{TestSupplyProxy.rejectReservationNumber=0;}
        assertThat(TestSupplyServer.activeReservations()).isEqualTo(before);assertThat(data(call("GET","/api/v1/orders",t,null)).path("data").size()).isZero();problem(order(t,"partial",body),409);
        TestSupplyProxy.loseReservationResponse=true;try{problem(order(t,"lost",body),503);}finally{TestSupplyProxy.loseReservationResponse=false;}
        assertThat(TestSupplyServer.activeReservations()).isEqualTo(before);assertThat(data(call("GET","/api/v1/orders",t,null)).path("data").size()).isZero();
    }
    @Test void failedOutboxInsertRollsBackOrderItemsAndHistoryAndReleasesRemoteStock()throws Exception {
        String t=token(),cart=create(t);var f=listing();stock(f,"1");data(call("POST","/"+cart+"/items",t,java.util.Map.of("version",0,"listingId",f.id(),"quantity",1)));var body=preview(cart,address(t,"02110"),DELIVERY_SLOT);long before=TestSupplyServer.activeReservations();
        try(var db=com.fresveg.testing.database.DatabaseFixture.admin(POSTGRES);var sql=db.createStatement()) {
            sql.execute("CREATE FUNCTION commerce.test_fail_outbox() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Test outbox failure'; END $$");sql.execute("CREATE TRIGGER test_outbox_failure BEFORE INSERT ON commerce.outbox_events FOR EACH ROW EXECUTE FUNCTION commerce.test_fail_outbox()");
            try {problem(order(t,"atomic",body),500);}finally{sql.execute("DROP TRIGGER test_outbox_failure ON commerce.outbox_events");sql.execute("DROP FUNCTION commerce.test_fail_outbox()");}
            assertThat(com.fresveg.testing.database.DatabaseFixture.scalar(db,"SELECT count(*) FROM commerce.orders WHERE cart_id='"+cart+"'")).isEqualTo("0");assertThat(com.fresveg.testing.database.DatabaseFixture.scalar(db,"SELECT count(*) FROM commerce.order_status_history h LEFT JOIN commerce.orders o USING(order_id) WHERE o.order_id IS NULL")).isEqualTo("0");
        }
        assertThat(TestSupplyServer.activeReservations()).isEqualTo(before);
    }
    @Test void cancellationFailureAndCrashedPreparedIntentAreRecoveredDurably()throws Exception {
        String t=token(),cart=create(t);var f=listing();stock(f,"2");data(call("POST","/"+cart+"/items",t,java.util.Map.of("version",0,"listingId",f.id(),"quantity",1)));var body=preview(cart,address(t,"02110"),DELIVERY_SLOT);long before=TestSupplyServer.activeReservations();
        var created=data(order(t,"cancel",body));UUID id=UUID.fromString(created.path("orderId").asString());TestSupplyProxy.failRelease=true;
        try{assertThat(data(call("POST","/api/v1/orders/"+id+"/cancel",t,null)).path("status").asString()).isEqualTo("CANCELLED");}finally{TestSupplyProxy.failRelease=false;}
        assertThat(TestSupplyServer.activeReservations()).isEqualTo(before);
        TestSupplyProxy.loseReservationResponse=true;TestSupplyProxy.failRelease=true;
        try{problem(order(t,"crash",body),503);}finally{TestSupplyProxy.loseReservationResponse=false;TestSupplyProxy.failRelease=false;}
        UUID intent;try(var db=com.fresveg.testing.database.DatabaseFixture.admin(POSTGRES);var sql=db.createStatement()) {intent=UUID.fromString(com.fresveg.testing.database.DatabaseFixture.scalar(db,"SELECT record_id::text FROM commerce.idempotency_records WHERE idempotency_key='crash'"));sql.execute("UPDATE commerce.idempotency_records SET status='PREPARED',updated_at=CURRENT_TIMESTAMP-INTERVAL '3 minutes' WHERE record_id='"+intent+"'");}
        assertThat(orderRepository.recovery()).contains(intent);orderService.recoverIntent(intent);assertThat(TestSupplyServer.activeReservations()).isEqualTo(before);
    }

    @Test void outboxPublishesCommittedEventsAndRetriesFailuresWithoutRollingBackOrder()throws Exception {
        String t=token(),cart=create(t);var f=listing();stock(f,"1");data(call("POST","/"+cart+"/items",t,java.util.Map.of("version",0,"listingId",f.id(),"quantity",1)));
        var result=data(order(t,"events",preview(cart,address(t,"02110"),DELIVERY_SLOT)));UUID id=UUID.fromString(result.path("orderId").asString());
        assertThat(outboxPublication.publishPending()).isGreaterThanOrEqualTo(3);
        assertThat(localOutbox.published()).extracting(e->e.eventType()).contains("OrderCreated","PaymentAuthorized","OrderConfirmed");
        try(var db=com.fresveg.testing.database.DatabaseFixture.admin(POSTGRES)) {
            assertThat(com.fresveg.testing.database.DatabaseFixture.scalar(db,"SELECT count(*) FROM commerce.outbox_events WHERE aggregate_id='"+id+"' AND status='PUBLISHED'")).isEqualTo("3");
            String payload=com.fresveg.testing.database.DatabaseFixture.scalar(db,"SELECT payload::text FROM commerce.outbox_events WHERE aggregate_id='"+id+"' AND event_type='OrderConfirmed' LIMIT 1");
            assertThat(payload).contains("\"eventVersion\": 1","\"correlationId\": \"cart-test\"","\"payload\"").doesNotContain("schemaVersion");
        }
        UUID failed=UUID.randomUUID();
        outboxRepository.insertForTest(failed,id,"ShipmentDelivered",mapper.writeValueAsString(java.util.Map.of("eventId",failed,"eventType","ShipmentDelivered","eventVersion",1,"aggregateId",id,"occurredAt",Instant.now(),"correlationId","cart-test","payload",java.util.Map.of("orderId",id))),java.sql.Timestamp.from(Instant.now()));
        assertThat(outboxPublication.publishPending()).isZero();
        try(var db=com.fresveg.testing.database.DatabaseFixture.admin(POSTGRES)) {
            assertThat(com.fresveg.testing.database.DatabaseFixture.scalar(db,"SELECT status || ':' || retry_count FROM commerce.outbox_events WHERE event_id='"+failed+"'")).isEqualTo("PENDING:1");
            assertThat(com.fresveg.testing.database.DatabaseFixture.scalar(db,"SELECT status FROM commerce.orders WHERE order_id='"+id+"'")).isEqualTo("CONFIRMED");
        }
        data(call("POST","/api/v1/orders/"+id+"/cancel",t,null));
    }

    @Test void vendorDecisionsRemainScopedAndCannotChangeConfirmedOrders()throws Exception {
        String t=token(),cart=create(t);var a=listing();var b=listing();stock(a,"1");stock(b,"1");
        data(call("POST","/"+cart+"/items",t,java.util.Map.of("version",0,"listingId",a.id(),"quantity",1)));data(call("POST","/"+cart+"/items",t,java.util.Map.of("version",1,"listingId",b.id(),"quantity",1)));
        var result=data(order(t,"vendors",preview(cart,address(t,"02110"),DELIVERY_SLOT)));String id=result.path("orderId").asString();UUID va=null,vb=null;
        for(var line:result.path("items")){if(line.path("listingId").asString().equals(a.id()))va=UUID.fromString(line.path("vendorId").asString());else vb=UUID.fromString(line.path("vendorId").asString());}
        String path="/api/v1/vendor/orders/"+id;var own=data(call("GET",path+"?vendorId="+va,a.vendorToken(),null));assertThat(own.path("items").size()).isEqualTo(1);assertThat(own.path("items").get(0).path("listingId").asString()).isEqualTo(a.id());assertThat(own.has("shippingAddress")).isFalse();
        problem(call("GET",path+"?vendorId="+vb,a.vendorToken(),null),403);problem(call("GET","/api/v1/vendor/orders/"+UUID.randomUUID()+"?vendorId="+va,a.vendorToken(),null),404);
        assertThat(data(call("GET","/api/v1/vendor/orders?vendorId="+va+"&pageSize=1",a.vendorToken(),null)).path("data").size()).isEqualTo(1);
        String staff=token();TestAccountServer.membership(staff,va,"VENDOR_STAFF");data(call("GET",path+"?vendorId="+va,staff,null));problem(call("POST",path+"/accept?vendorId="+va,staff,java.util.Map.of("version",0)),403);
        problem(call("POST",path+"/accept?vendorId="+va,a.vendorToken(),java.util.Map.of("version",2)),409);
        problem(call("POST",path+"/reject?vendorId="+vb,b.vendorToken(),java.util.Map.of("version",2)),409);
        assertThat(data(call("GET","/api/v1/orders/"+id,t,null)).path("status").asString()).isEqualTo("CONFIRMED");
    }
    @Test void declinedPaymentReleasesInventoryAndCanBeCancelled()throws Exception {
        String t=token(),cart=create(t);var f=listing();stock(f,"1");data(call("POST","/"+cart+"/items",t,java.util.Map.of("version",0,"listingId",f.id(),"quantity",1)));long before=TestSupplyServer.activeReservations();
        var body=new java.util.LinkedHashMap<>(preview(cart,address(t,"02110"),DELIVERY_SLOT));body.put("paymentMethodId",DECLINED_PAYMENT);
        var result=data(order(t,"declined",body));UUID id=UUID.fromString(result.path("orderId").asString());assertThat(result.path("status").asString()).isEqualTo("PAYMENT_FAILED");assertThat(TestSupplyServer.activeReservations()).isEqualTo(before);
        assertThat(data(call("POST","/api/v1/orders/"+id+"/cancel",t,null)).path("status").asString()).isEqualTo("CANCELLED");
        assertThat(data(call("GET","/api/v1/orders/"+id,t,null)).path("status").asString()).isEqualTo("CANCELLED");assertThat(TestSupplyServer.activeReservations()).isEqualTo(before);
        try(var db=com.fresveg.testing.database.DatabaseFixture.admin(POSTGRES)){assertThat(com.fresveg.testing.database.DatabaseFixture.scalar(db,"SELECT count(*) FROM commerce.payment_attempts WHERE order_id='"+id+"' AND status='FAILED'")).isEqualTo("1");assertThat(com.fresveg.testing.database.DatabaseFixture.scalar(db,"SELECT count(*) FROM commerce.outbox_events WHERE aggregate_id='"+id+"' AND event_type IN ('PaymentFailed','OrderCancelled')")).isEqualTo("2");}
    }

    private HttpResponse<String> order(String token,String key,Object body)throws Exception {
        if(body instanceof java.util.Map<?,?> m && !m.containsKey("paymentMethodId") && key!=null) {
            var copy=new java.util.LinkedHashMap<String,Object>();m.forEach((k,v)->copy.put(String.valueOf(k),v));
            copy.put("paymentMethodId",UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)));body=copy;
        }
        var r=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/orders")).timeout(Duration.ofSeconds(110)).header("X-Correlation-ID","cart-test").header("Content-Type","application/json");if(token!=null)r.header("Authorization","Bearer "+token);if(key!=null)r.header("Idempotency-Key",key);
        try(var client=HttpClient.newHttpClient()){return client.send(r.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());}
    }

    private static java.util.Map<String,Object> preview(String cart,UUID address,UUID slot){return java.util.Map.of("cartId",cart,"deliveryAddressId",address,"deliverySlotId",slot);}
    private UUID address(String token,String postal)throws Exception {
        try(var client=HttpClient.newHttpClient()) {
            var body=java.util.Map.of("recipientName","Customer","line1","1 Road","city","Boston","postalCode",postal,"countryCode","US");
            var r=client.send(HttpRequest.newBuilder(URI.create(TestAccountServer.url()+"/api/v1/accounts/me/addresses")).header("Authorization","Bearer "+token).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(201);return UUID.fromString(mapper.readTree(r.body()).path("data").path("addressId").asString());
        }
    }
    private void stock(Listing f,String quantity)throws Exception {
        String id=TestSupplyServer.mutate("POST","/vendor/inventory",f.vendorToken(),java.util.Map.of("listingId",f.id())).path("inventoryId").asString();
        TestSupplyServer.mutate("POST","/vendor/inventory/"+id+"/batches",f.vendorToken(),java.util.Map.of("version",0,"batchNumber","INITIAL","receivedDate",java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString(),"expiryDate",java.time.LocalDate.now(java.time.ZoneOffset.UTC).plusDays(3).toString(),"origin","Farm","grade","A","certificationData",java.util.Map.of(),"quantity",new BigDecimal(quantity)));
    }
    private String commerceState(String id)throws Exception {
        try(var db=com.fresveg.testing.database.DatabaseFixture.admin(POSTGRES)){return com.fresveg.testing.database.DatabaseFixture.scalar(db,"SELECT row_to_json(c)::text || (SELECT json_agg(i ORDER BY item_id)::text FROM commerce.cart_items i WHERE cart_id=c.cart_id) FROM commerce.carts c WHERE cart_id='"+id+"'");}
    }

    private String create(String token)throws Exception {return data(call("POST","",token,java.util.Map.of("currency","USD"))).path("cartId").asString();}
    private static String token()throws Exception{return TestIdentityProvider.token(UUID.randomUUID().toString());}
    private record Listing(String id,String vendorToken,java.util.Map<String,Object> input) { }
    private Listing listing()throws Exception {
        String t=token();UUID vendor=TestAccountServer.vendor(t,"VENDOR_ADMIN");var product=TestCatalogServer.product();
        String location=TestSupplyServer.mutate("POST","/vendor/locations?vendorId="+vendor,t,java.util.Map.of("code","L"+UUID.randomUUID().toString().replace("-","").toUpperCase(),"name","Farm","line1","Road","city","Boston","postalCode","02110","countryCode","US","status","ACTIVE")).path("locationId").asString();
        var body=new java.util.LinkedHashMap<String,Object>();body.put("locationId",location);body.put("productId",product.path("productId").asString());body.put("variantId",product.path("variants").get(0).path("variantId").asString());body.put("vendorSku","SKU"+UUID.randomUUID().toString().replace("-","").toUpperCase());body.put("uomCode","KG");body.put("minimumOrderQuantity",1);body.put("status","ACTIVE");body.put("attributes",java.util.Map.of());
        var price=price("2.750001");price.put("tiers",java.util.List.of(java.util.Map.of("minQuantity",5,"unitPrice",new BigDecimal("2.125001"))));body.put("prices",java.util.List.of(price));
        return new Listing(TestSupplyServer.mutate("POST","/vendor/listings?vendorId="+vendor,t,body).path("listingId").asString(),t,body);
    }
    private static java.util.Map<String,Object> price(String amount) {return new java.util.LinkedHashMap<>(java.util.Map.of("currency","USD","unitPrice",new BigDecimal(amount),"minQuantity",1,"validFrom","2026-01-01T00:00:00Z","tiers",java.util.List.of()));}
    private HttpResponse<String> call(String method,String path,String token,Object body)throws Exception {
        var r=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+(path.startsWith("/api/")?path:"/api/v1/carts"+path))).timeout(Duration.ofSeconds(30)).header("X-Correlation-ID","cart-test");if(token!=null)r.header("Authorization","Bearer "+token);if(body!=null)r.header("Content-Type","application/json");r.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));try(var client=HttpClient.newHttpClient()){return client.send(r.build(),HttpResponse.BodyHandlers.ofString());}
    }
    private tools.jackson.databind.JsonNode data(HttpResponse<String> r){assertThat(r.statusCode()).withFailMessage(r.body()).isIn(200,201);var j=mapper.readTree(r.body());assertThat(j.path("meta").path("requestId").asString()).isEqualTo("cart-test");return j.path("data");}
    private void problem(HttpResponse<String> r,int code){assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(code);assertThat(r.headers().firstValue("content-type")).contains("application/problem+json");assertThat(mapper.readTree(r.body()).path("correlationId").asString()).isEqualTo("cart-test");}

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
