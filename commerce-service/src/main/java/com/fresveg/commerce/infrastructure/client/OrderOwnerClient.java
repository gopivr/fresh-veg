package com.fresveg.commerce.infrastructure.client;

import com.fresveg.commerce.application.order.*;
import com.fresveg.commerce.api.dto.OrderContracts.OrderLine;
import com.fresveg.common.http.CorrelationIds;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.*;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

/** Service bearer is externally issued and read from a rotatable mounted file on every call. */
@Component
public class OrderOwnerClient implements DisposableBean {
 private final String supply,catalog,tokenFile;
 private final JsonMapper json;
 private final HttpClient client;
 private final Duration timeout;
 private final Semaphore permits;
 public OrderOwnerClient(@Value("${commerce.supply-base-url}") String supply,@Value("${commerce.orders.catalog-base-url:}") String catalog,
 @Value("${commerce.orders.inventory-token-file:}") String tokenFile,JsonMapper json,
 @Value("${commerce.client.connect-timeout-ms:2000}") int connect,@Value("${commerce.client.request-timeout-ms:3000}") int timeout,
 @Value("${commerce.client.max-concurrent:8}") int permits) {
  this.supply=base(supply);this.catalog=catalog.isBlank()?"":base(catalog);this.tokenFile=tokenFile;
  this.json=json.rebuild().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
  this.client=HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connect)).followRedirects(HttpClient.Redirect.NEVER).build();
  this.timeout=Duration.ofMillis(timeout);this.permits=new Semaphore(permits);
 }
 public JsonNode product(UUID id) {
  if(catalog.isEmpty())throw OwnerHttp.unavailable();
  var p=call("GET",catalog+"/api/v1/catalog/products/"+id,null,false);
  if(p==null || !id.toString().equals(p.path("productId").asString()) || !"ACTIVE".equals(p.path("status").asString()))throw OrderErrors.conflict("Product is unavailable.");
  return p;
 }
 public UUID reserve(OrderPlan plan,OrderLine line) {
  var r=call("POST",supply+"/internal/v1/inventory/order-reservations",Map.of("listingId",line.listingId(),"externalReference",plan.reference(line),"quantity",line.quantity(),"expiresAt",plan.expiresAt(),"requiredUntil",plan.preview().deliverySlot().endsAt()),true);
  try {
   if(r==null || !"ACTIVE".equals(r.path("status").asString()) || !plan.reference(line).equals(r.path("externalReference").asString()) || r.path("quantity").decimalValue().compareTo(line.quantity())!=0 || !Instant.parse(r.path("expiresAt").asString()).equals(plan.expiresAt()))throw new IllegalStateException();
   return UUID.fromString(r.path("reservationId").asString());
  } catch(Exception error) { throw OwnerHttp.unavailable(); }
 }
 public void release(OrderPlan plan,OrderLine line,boolean mustExist) {
  var r=call("GET",supply+"/internal/v1/inventory/reservations/by-reference?externalReference="+URLEncoder.encode(plan.reference(line),java.nio.charset.StandardCharsets.UTF_8),null,true);
  if(r==null) {if(mustExist)throw OwnerHttp.unavailable();return;}
  if(!plan.reference(line).equals(r.path("externalReference").asString()))throw OwnerHttp.unavailable();
  final UUID id;
  try {id=UUID.fromString(r.path("reservationId").asString());}catch(Exception e){throw OwnerHttp.unavailable();}
  var result=call("POST",supply+"/internal/v1/inventory/reservations/"+id+"/release",null,true);
  if(result==null || !id.toString().equals(result.path("reservationId").asString()) || !Set.of("RELEASED","EXPIRED").contains(result.path("status").asString()))throw OwnerHttp.unavailable();
 }
 public void commit(OrderPlan plan,OrderLine line) {
  var r=call("GET",supply+"/internal/v1/inventory/reservations/by-reference?externalReference="+URLEncoder.encode(plan.reference(line),java.nio.charset.StandardCharsets.UTF_8),null,true);
  if(r==null || !plan.reference(line).equals(r.path("externalReference").asString()))throw OwnerHttp.unavailable();
  final UUID id;
  try {id=UUID.fromString(r.path("reservationId").asString());}catch(Exception e){throw OwnerHttp.unavailable();}
  var result=call("POST",supply+"/internal/v1/inventory/reservations/"+id+"/commit",null,true);
  if(result==null || !id.toString().equals(result.path("reservationId").asString()) || !"COMMITTED".equals(result.path("status").asString()))throw OwnerHttp.unavailable();
 }
 private JsonNode call(String method,String url,Object body,boolean service) {
  if(!permits.tryAcquire())throw OwnerHttp.unavailable();
  try {
   var b=HttpRequest.newBuilder(URI.create(url)).timeout(timeout).header("Content-Type","application/json");
   if(service) {
    if(tokenFile.isBlank() || Files.size(Path.of(tokenFile))>16384)throw new IllegalStateException();
    String token=Files.readString(Path.of(tokenFile)).strip();if(token.isEmpty())throw new IllegalStateException();b.header("Authorization","Bearer "+token);
   }
   if(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a && a.getRequest().getAttribute(CorrelationIds.CONTEXT_KEY) instanceof String id)b.header(CorrelationIds.HEADER,id);
   var r=client.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
   if(r.statusCode()==404)return null;
   if(r.statusCode()==409)throw OrderErrors.conflict("Inventory reservation conflicts with available stock or its lifecycle.");
   if(r.statusCode()!=200 || r.body().length()>1048576)throw new IllegalStateException();
   var data=json.readTree(r.body()).path("data");if(!data.isObject())throw new IllegalStateException();return data;
  } catch(com.fresveg.commerce.application.CommerceException e) {throw e;}
  catch(Exception e) {if(e instanceof InterruptedException)Thread.currentThread().interrupt();throw OwnerHttp.unavailable();}
  finally {permits.release();}
 }
 private static String base(String value) { var u=URI.create(value);if(!Set.of("http","https").contains(u.getScheme()) || u.getHost()==null || u.getUserInfo()!=null || u.getQuery()!=null || u.getFragment()!=null)throw new IllegalArgumentException("Trusted owner URL required");return value.replaceAll("/+$",""); }
 @Override public void destroy() {client.close();}
}
