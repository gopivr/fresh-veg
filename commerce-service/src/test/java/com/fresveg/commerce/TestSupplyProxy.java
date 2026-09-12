package com.fresveg.commerce;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
/** Test-only fault injection in front of the real packaged Supply service. */
final class TestSupplyProxy {
 private static HttpServer server;
 static volatile boolean unavailable,failRelease,loseReservationResponse,hideReservation;
 static final java.util.concurrent.atomic.AtomicInteger reserveCalls=new java.util.concurrent.atomic.AtomicInteger();
 static volatile int rejectReservationNumber;
 static synchronized String url() {
  if(server==null) try {
   String upstream=TestSupplyServer.url();server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
   server.createContext("/",e->{
    int status=503;byte[] bytes="{}".getBytes(StandardCharsets.UTF_8);
    if(hideReservation && e.getRequestURI().getPath().endsWith("/by-reference"))status=404;
    else if(!unavailable && !(failRelease && e.getRequestURI().getPath().endsWith("/release"))) try(var client=HttpClient.newHttpClient()) {
     var request=HttpRequest.newBuilder(URI.create(upstream+e.getRequestURI())).timeout(Duration.ofSeconds(10));String correlation=e.getRequestHeaders().getFirst("X-Correlation-ID");if(correlation!=null)request.header("X-Correlation-ID",correlation);
     String auth=e.getRequestHeaders().getFirst("Authorization");if(auth!=null)request.header("Authorization",auth);
     request.header("Content-Type","application/json").method(e.getRequestMethod(),HttpRequest.BodyPublishers.ofByteArray(e.getRequestBody().readAllBytes()));
     boolean reserve=e.getRequestURI().getPath().equals("/internal/v1/inventory/order-reservations");
     if(reserve && reserveCalls.incrementAndGet()==rejectReservationNumber) {status=409;}
     else {var result=client.send(request.build(),HttpResponse.BodyHandlers.ofByteArray());status=result.statusCode();bytes=result.body();if(reserve && loseReservationResponse){status=503;bytes="{}".getBytes(StandardCharsets.UTF_8);}}

    } catch(Exception ignored) { }
    e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(status,bytes.length);try(var out=e.getResponseBody()){out.write(bytes);}
   });server.start();
  } catch(Exception error) {throw new IllegalStateException(error);}
  return "http://127.0.0.1:"+server.getAddress().getPort();
 }
 static synchronized void stop(){if(server!=null)server.stop(0);server=null;unavailable=false;}
}
