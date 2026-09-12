package com.fresveg.commerce.infrastructure.client;

import com.fresveg.commerce.application.CommerceException;
import com.fresveg.common.http.CorrelationIds;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.*;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.*;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

@Component
public class OwnerHttp implements DisposableBean {
 private final String account,supply;
 private final JsonMapper json;
 private final HttpClient client;
 private final Duration timeout,cooldown;
 private final Gate accountGate,supplyGate;
 public OwnerHttp(String account,String supply,JsonMapper json) { this(account,supply,json,2000,3000,5000,8); }
 @org.springframework.beans.factory.annotation.Autowired
 public OwnerHttp(@Value("${commerce.account-base-url}") String account,@Value("${commerce.supply-base-url}") String supply,JsonMapper json,
 @Value("${commerce.client.connect-timeout-ms:2000}") int connectTimeout,
 @Value("${commerce.client.request-timeout-ms:3000}") int requestTimeout,
 @Value("${commerce.client.cooldown-ms:5000}") int cooldownMillis,
 @Value("${commerce.client.max-concurrent:8}") int permits) {
  if(connectTimeout<1 || requestTimeout<1 || cooldownMillis<1 || permits<1 || permits>100) throw new IllegalArgumentException("Positive bounded owner-client settings required");
  client=HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeout)).followRedirects(HttpClient.Redirect.NEVER).build();
  timeout=Duration.ofMillis(requestTimeout);cooldown=Duration.ofMillis(cooldownMillis);accountGate=new Gate(permits);supplyGate=new Gate(permits);
  this.account=base(account);this.supply=base(supply);this.json=json.rebuild().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
 }
 public JsonNode account() { return get(account+"/api/v1/accounts/me",true,accountGate).path("data"); }
 public JsonNode memberships(java.util.UUID cursor) {return get(account+"/api/v1/accounts/me/vendor-memberships?pageSize=100"+(cursor==null?"":"&cursor="+cursor),true,accountGate);}
 public JsonNode vendor(java.util.UUID id) { return get(account+"/api/v1/accounts/vendors/"+id,true,accountGate).path("data"); }
 public JsonNode listing(java.util.UUID id) { var result=get(supply+"/api/v1/supply/listings/"+id,false,supplyGate);return result==null?null:result.path("data"); }
 public JsonNode addresses(java.util.UUID cursor) { return get(account+"/api/v1/accounts/me/addresses?pageSize=100"+(cursor==null?"":"&cursor="+cursor),true,accountGate); }
 public JsonNode availability(java.util.UUID listing,java.math.BigDecimal quantity,java.time.Instant until) {
  var result=get(supply+"/api/v1/supply/listings/"+listing+"/availability?quantity="+quantity.toPlainString()+"&requiredUntil="+java.net.URLEncoder.encode(until.toString(),java.nio.charset.StandardCharsets.UTF_8),false,supplyGate);
  return result==null?null:result.path("data");
 }
 private JsonNode get(String url,boolean authenticated,Gate gate) {
  if(System.nanoTime()<gate.openUntil.get() || !gate.slots.tryAcquire()) throw unavailable();
  try {
   var request=HttpRequest.newBuilder(URI.create(url)).timeout(timeout);
   if(authenticated) {
    if(!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()) throw new AccessDeniedException("Validated identity required");
    request.header("Authorization","Bearer "+jwt.getToken().getTokenValue());
   }
   if(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes && attributes.getRequest().getAttribute(CorrelationIds.CONTEXT_KEY) instanceof String id) request.header(CorrelationIds.HEADER,id);
   var response=client.send(request.build(),HttpResponse.BodyHandlers.ofString());
   if(authenticated && (response.statusCode()==401 || response.statusCode()==403)) { gate.failures.set(0);throw new AccessDeniedException("Account identity denied"); }
   if(!authenticated && response.statusCode()==404) { gate.failures.set(0);return null; }
   if(response.statusCode()!=200 || response.body().length()>1_048_576) throw new IllegalStateException();
   var result=json.readTree(response.body());if(!result.isObject() || !(result.path("data").isObject() || result.path("data").isArray())) throw new IllegalStateException();
   gate.failures.set(0);return result;
  } catch(AccessDeniedException e) { throw e; }
  catch(Exception e) {
   if(e instanceof InterruptedException) Thread.currentThread().interrupt();
   if(gate.failures.incrementAndGet()>=3) { gate.openUntil.set(System.nanoTime()+cooldown.toNanos());gate.failures.set(0); }
   throw unavailable();
  } finally { gate.slots.release(); }
 }
 private static String base(String value) {
  var uri=URI.create(value);
  if(!Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null) throw new IllegalArgumentException("Trusted owner URL required");
  return value.replaceAll("/+$","");
 }
 public static CommerceException unavailable() { return new CommerceException(HttpStatus.SERVICE_UNAVAILABLE,"CRT-503-001","A required owner service is temporarily unavailable."); }
 private static class Gate { final Semaphore slots;Gate(int permits) { slots=new Semaphore(permits); }final AtomicInteger failures=new AtomicInteger();final AtomicLong openUntil=new AtomicLong(); }
 @Override public void destroy() { client.close(); }
}
