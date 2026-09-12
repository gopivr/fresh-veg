package com.fresveg.commerce.infrastructure.client;
import static org.assertj.core.api.Assertions.*;
import com.fresveg.commerce.application.CommerceException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import tools.jackson.databind.json.JsonMapper;
class HttpSupplyClientTest {
 private HttpServer server;private OwnerHttp http;private HttpSupplyClient client;
 private final AtomicInteger calls=new AtomicInteger();private volatile int status=200;private volatile String body;private volatile long delay;
 private final UUID listing=UUID.randomUUID(),price=UUID.randomUUID();
 @BeforeEach void start()throws Exception {
  body="{\"data\":{\"listingId\":\""+listing+"\",\"status\":\"ACTIVE\",\"minimumOrderQuantity\":1,\"prices\":[{\"priceId\":\""+price+"\",\"currency\":\"USD\",\"unitPrice\":9000000000000.123456,\"minQuantity\":1,\"validFrom\":\"2026-01-01T00:00:00Z\",\"validTo\":null,\"status\":\"ACTIVE\",\"tiers\":[]}]}}";
  server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",e->{calls.incrementAndGet();try {if(delay>0)Thread.sleep(delay);byte[] data=body.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(status,data.length);try(var out=e.getResponseBody()){out.write(data);}}catch(Exception ignored){e.close();}});server.start();
  String base="http://127.0.0.1:"+server.getAddress().getPort();http=new OwnerHttp(base,base,new JsonMapper());client=new HttpSupplyClient(http,Clock.fixed(Instant.parse("2026-09-09T12:00:00Z"),ZoneOffset.UTC));
 }
 @AfterEach void stop(){server.stop(0);http.destroy();}
 @Test void exactDecimalsAndCurrentStatusComeOnlyFromSupply(){
  assertThat(client.quote(listing,"USD",BigDecimal.ONE).price().unitPrice()).isEqualByComparingTo("9000000000000.123456");
  assertThat(client.quote(listing,"EUR",BigDecimal.ONE).status()).isEqualTo("NO_CURRENT_PRICE");
  assertThat(client.quote(listing,"USD",new BigDecimal("0.5")).status()).isEqualTo("MINIMUM_NOT_MET");
  status=404;assertThat(client.quote(listing,"USD",BigDecimal.ONE).status()).isEqualTo("UNAVAILABLE");
 }
 @Test void malformedOwnerDataNeverFallsBackToAnEarlierPrice(){
  client.quote(listing,"USD",BigDecimal.ONE);body="{\"data\":{\"listingId\":\"wrong\"}}";
  assertThatThrownBy(()->client.quote(listing,"USD",BigDecimal.ONE)).isInstanceOf(CommerceException.class).satisfies(e->assertThat(((CommerceException)e).status().value()).isEqualTo(503));
 }
 @Test void repeatedOutageOpensCircuitAndAvoidsExtraNetworkCalls(){
  status=503;for(int i=0;i<4;i++)assertThatThrownBy(()->client.quote(listing,"USD",BigDecimal.ONE)).isInstanceOf(CommerceException.class);
  assertThat(calls.get()).isEqualTo(3);
 }
 @Test void slowOwnerHasBoundedTimeout(){
  delay=5000;long start=System.nanoTime();assertThatThrownBy(()->client.quote(listing,"USD",BigDecimal.ONE)).isInstanceOf(CommerceException.class);
  assertThat(Duration.ofNanos(System.nanoTime()-start)).isLessThan(Duration.ofSeconds(4));
 }
}
