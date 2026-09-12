package com.fresveg.commerce.infrastructure.client;
import static org.assertj.core.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.json.JsonMapper;
class CheckoutAddressClientTest {
 private HttpServer server;private OwnerHttp http;private CheckoutAddressClient addresses;private final UUID id=UUID.randomUUID(),cursor=UUID.randomUUID();
 private volatile boolean repeat,malformed;private final AtomicInteger calls=new AtomicInteger();
 @BeforeEach void start()throws Exception {
  server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",e->{calls.incrementAndGet();assertThat(e.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer fixture");
   String body=malformed?"{\"data\":[],\"pagination\":{}}":(repeat || !e.getRequestURI().getQuery().contains("cursor="))?"{\"data\":[],\"pagination\":{\"hasNext\":true,\"nextCursor\":\""+cursor+"\"}}":"{\"data\":[{\"addressId\":\""+id+"\",\"recipientName\":\"Customer\",\"line1\":\"Road\",\"line2\":null,\"city\":\"Boston\",\"region\":null,\"postalCode\":\"02110\",\"countryCode\":\"US\",\"phone\":null,\"version\":2}],\"pagination\":{\"hasNext\":false,\"nextCursor\":null}}";
   byte[] bytes=body.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,bytes.length);try(var out=e.getResponseBody()){out.write(bytes);}
  });server.start();String base="http://127.0.0.1:"+server.getAddress().getPort();http=new OwnerHttp(base,base,new JsonMapper());addresses=new CheckoutAddressClient(http);
  SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("fixture").header("alg","RS256").subject("customer").build(),java.util.List.of()));
 }
 @AfterEach void stop(){SecurityContextHolder.clearContext();server.stop(0);http.destroy();}
 @Test void ownedAddressCanAppearOnALaterPage(){assertThat(addresses.owned(id,System.nanoTime()+Duration.ofSeconds(5).toNanos()).version()).isEqualTo(2);assertThat(calls.get()).isEqualTo(2);}
 @Test void repeatedCursorMalformedPaginationAndMissingAddressFailClosed(){
  repeat=true;assertThatThrownBy(()->addresses.owned(id,Long.MAX_VALUE)).isInstanceOf(com.fresveg.commerce.application.CommerceException.class);assertThat(calls.get()).isEqualTo(2);
  repeat=false;malformed=true;assertThatThrownBy(()->addresses.owned(id,Long.MAX_VALUE)).isInstanceOf(com.fresveg.commerce.application.CommerceException.class);
  malformed=false;assertThatThrownBy(()->addresses.owned(UUID.randomUUID(),Long.MAX_VALUE)).hasMessageContaining("Owned resource not found");
 }
}
