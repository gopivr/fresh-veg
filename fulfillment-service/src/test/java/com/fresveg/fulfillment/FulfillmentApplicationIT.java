package com.fresveg.fulfillment;
import static org.assertj.core.api.Assertions.assertThat;
import com.fresveg.common.http.CorrelationIds;
import com.fresveg.testing.database.ServiceDatabaseTest;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.test.context.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FulfillmentApplicationIT extends ServiceDatabaseTest {
 static final String SERVICE_SUBJECT="11111111-1111-1111-1111-111111111111";
 @LocalServerPort int port; @Autowired JsonMapper mapper; @Autowired Environment environment;
 @DynamicPropertySource static void props(DynamicPropertyRegistry r){r.add("fulfillment.security.issuer-uri",()->TestIdentityProvider.ISSUER);r.add("fulfillment.security.audience",()->"fresveg-fulfillment");r.add("fulfillment.security.jwk-set-uri",TestIdentityProvider::jwksUri);r.add("fulfillment.service-subjects",()->SERVICE_SUBJECT);}
 @ParameterizedTest @ValueSource(strings={"/actuator/health","/actuator/health/liveness","/actuator/health/readiness"})
 void probesAreHealthyAndDoNotDiscloseComponents(String path)throws Exception{var res=get(path,null,"phase1-probe");assertThat(res.statusCode()).isEqualTo(200);assertThat(mapper.readTree(res.body()).get("status").asString()).isEqualTo("UP");assertThat(mapper.readTree(res.body()).has("components")).isFalse();assertThat(res.headers().firstValue(CorrelationIds.HEADER)).contains("phase1-probe");}
 @Test void missingAndUnsafeCorrelationIdsAreReplaced()throws Exception{for(String supplied:new String[]{null,"unsafe/id","a".repeat(129)}){var r=get("/actuator/health",null,supplied);String id=r.headers().firstValue(CorrelationIds.HEADER).orElseThrow();assertThat(UUID.fromString(id).toString()).isEqualTo(id);}}
 @ParameterizedTest @ValueSource(strings={"/api/v1/fulfillments/00000000-0000-0000-0000-000000000001","/internal/v1/fulfillments","/actuator/env","/login"})
 void protectedRequestsDenyWithoutLoginRedirect(String path)throws Exception{var res=path.startsWith("/internal")?post(path,"{}",null,"denied-request"):get(path,null,"denied-request");assertThat(res.statusCode()).isIn(401,403);assertThat(res.headers().firstValue("content-type")).contains("application/problem+json");assertThat(res.headers().firstValue("location")).isEmpty();assertThat(res.headers().firstValue(CorrelationIds.HEADER)).contains("denied-request");}
 @Test void fulfillmentCreationTrackingAndTransitionsWork()throws Exception{
  Instant start=Instant.now().plusSeconds(7200).truncatedTo(java.time.temporal.ChronoUnit.MICROS), end=start.plusSeconds(3600);
  var slot=post("/internal/v1/fulfillment/slots",json(Map.of("serviceArea","north","startTime",start,"endTime",end,"capacity",1,"status","OPEN")),serviceToken(),"fulfillment-test");
  assertThat(slot.statusCode()).isEqualTo(200);UUID slotId=UUID.fromString(data(slot).get("slotId").asString());
  var unfiltered=get("/api/v1/fulfillment/slots?pageSize=10",null,"fulfillment-test");assertThat(unfiltered.statusCode()).isEqualTo(200);assertThat(data(unfiltered).size()).isEqualTo(1);
  var listed=get("/api/v1/fulfillment/slots?serviceArea=north&pageSize=10",null,"fulfillment-test");assertThat(listed.statusCode()).isEqualTo(200);assertThat(data(listed).size()).isEqualTo(1);
  String createBody=fulfillmentBody(UUID.randomUUID(),slotId,start,end,UUID.randomUUID());
  var create=post("/internal/v1/fulfillments",createBody,serviceToken(),"fulfillment-test");
  assertThat(create.statusCode()).isEqualTo(200);JsonNode created=data(create);UUID fulfillment=UUID.fromString(created.get("fulfillmentId").asString());assertThat(created.get("status").asString()).isEqualTo("CREATED");assertThat(created.get("items").size()).isEqualTo(1);assertThat(created.get("shipments").get(0).get("status").asString()).isEqualTo("PENDING");
  var replay=post("/internal/v1/fulfillments",createBody,serviceToken(),"fulfillment-test");assertThat(replay.statusCode()).isEqualTo(200);assertThat(data(replay).get("fulfillmentId").asString()).isEqualTo(fulfillment.toString());
  var read=get("/api/v1/fulfillments/"+fulfillment,userToken(),"fulfillment-test");assertThat(read.statusCode()).isEqualTo(200);
  var packing=post("/internal/v1/fulfillments/"+fulfillment+"/transitions",json(Map.of("status","PACKING","version",created.get("version").asLong(),"description","Packing started")),serviceToken(),"fulfillment-test");assertThat(packing.statusCode()).isEqualTo(200);
  var ready=post("/internal/v1/fulfillments/"+fulfillment+"/transitions",json(Map.of("status","READY_FOR_DELIVERY","version",data(packing).get("version").asLong())),serviceToken(),"fulfillment-test");assertThat(ready.statusCode()).isEqualTo(200);
  var out=post("/internal/v1/fulfillments/"+fulfillment+"/transitions",json(Map.of("status","OUT_FOR_DELIVERY","version",data(ready).get("version").asLong(),"description","Courier departed")),serviceToken(),"fulfillment-test");assertThat(out.statusCode()).isEqualTo(200);
  var tracking=get("/api/v1/fulfillments/"+fulfillment+"/tracking",userToken(),"fulfillment-test");assertThat(tracking.statusCode()).isEqualTo(200);assertThat(data(tracking).get("shipments").get(0).get("events").size()).isGreaterThanOrEqualTo(2);
  var full=post("/internal/v1/fulfillments",fulfillmentBody(UUID.randomUUID(),slotId,start,end,UUID.randomUUID()),serviceToken(),"fulfillment-test");assertThat(full.statusCode()).isEqualTo(409);
 }
 @Test void openApiListsFulfillmentRoutesAndWritesContract()throws Exception{var spec=mapper.readTree(get("/v3/api-docs",null,"openapi").body());assertThat(spec.path("paths").size()).isEqualTo(6);assertThat(spec.path("paths").toString()).contains("/api/v1/fulfillment/slots","/api/v1/fulfillments/{id}","/internal/v1/fulfillments");var yaml=get("/v3/api-docs.yaml",null,"openapi");assertThat(yaml.statusCode()).isEqualTo(200);java.nio.file.Files.writeString(java.nio.file.Path.of("target/fulfillment-api.yaml"),yaml.body());}
 @Test void serializationPreservesCamelCaseIsoTimeAndDecimalPrecision(){var value=new SerializationProbe("request-1",Instant.parse("2026-09-07T12:00:00Z"),new BigDecimal("1234567890.123456789"));String json=mapper.writeValueAsString(value);assertThat(json).contains("\"requestId\":\"request-1\"","\"occurredAt\":\"2026-09-07T12:00:00Z\"","\"amount\":1234567890.123456789");assertThat(mapper.readValue(json,SerializationProbe.class)).isEqualTo(value);}
 @Test void applicationUsesItsOwnConfiguration(){assertThat(environment.getProperty("spring.application.name")).isEqualTo("fulfillment-service");assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");assertThat(environment.getProperty("spring.sql.init.mode")).isEqualTo("never");}
 private String fulfillmentBody(UUID order,UUID slot,Instant start,Instant end,UUID customer){return json(Map.of("orderId",order,"customerId",customer,"deliverySlotId",slot,"serviceArea","north","requestedStart",start,"requestedEnd",end,"deliveryAddress",Map.of("line1","1 Market","city","Testville"),"items",List.of(Map.of("orderItemId",UUID.randomUUID(),"productId",UUID.randomUUID(),"listingId",UUID.randomUUID(),"vendorId",UUID.randomUUID(),"productName","Carrots","quantity",new BigDecimal("2.500000"),"unitCode","kg"))));}
 private String serviceToken()throws Exception{return TestIdentityProvider.token(SERVICE_SUBJECT);} private String userToken()throws Exception{return TestIdentityProvider.token(UUID.randomUUID().toString());}
 private String json(Object o){return mapper.writeValueAsString(o);} private JsonNode data(HttpResponse<String> r){return mapper.readTree(r.body()).get("data");}
 private HttpResponse<String> get(String path,String token,String cid)throws Exception{return request("GET",path,null,token,cid);}
 private HttpResponse<String> post(String path,String body,String token,String cid)throws Exception{return request("POST",path,body,token,cid);}
 private HttpResponse<String> request(String method,String path,String body,String token,String cid)throws Exception{var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(10));if(cid!=null)b.header(CorrelationIds.HEADER,cid);if(token!=null)b.header("Authorization","Bearer "+token);if(body!=null)b.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(body));else b.GET();try(var c=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()){return c.send(b.build(),HttpResponse.BodyHandlers.ofString());}}
 record SerializationProbe(String requestId,Instant occurredAt,BigDecimal amount){}
}
