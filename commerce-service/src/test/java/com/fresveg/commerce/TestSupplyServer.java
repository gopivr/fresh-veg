package com.fresveg.commerce;

import com.fresveg.testing.database.DatabaseFixture;
import java.net.*;
import java.net.http.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/** Runs the unmodified, packaged Account service on its own isolated PostgreSQL database. */
final class TestSupplyServer {
    private static PostgreSQLContainer postgres;
    private static Process process;
    private static String base;
    static synchronized String url() {
        if (base!=null) { return base; }
        try {
            var jar=Path.of("../supply-service/target/supply-service-0.1.0-SNAPSHOT.jar").toAbsolutePath();
            if (!java.nio.file.Files.isRegularFile(jar)) { throw new IllegalStateException("Run the full Maven reactor to build the Account package first"); }
            postgres=DatabaseFixture.container(); postgres.start(); DatabaseFixture.bootstrap(postgres);
            int port;
            try (var socket=new ServerSocket(0,0,InetAddress.getLoopbackAddress())) { port=socket.getLocalPort(); }
            String address="http://127.0.0.1:"+port;
            var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                    "-Xmx256m","-XX:ActiveProcessorCount=2","-jar",jar.toString(),"--spring.profiles.active=default");
            builder.environment().putAll(java.util.Map.of("SERVER_PORT",String.valueOf(port),"SERVER_ADDRESS","127.0.0.1",
                    "DB_URL",postgres.getJdbcUrl(),"DB_PASSWORD",DatabaseFixture.PASSWORD,"MIGRATION_DB_PASSWORD",DatabaseFixture.PASSWORD,
                    "DB_USER","fresveg_supply","MIGRATION_DB_USER","fresveg_supply_migrator",
                    "OIDC_ISSUER_URI",TestIdentityProvider.ISSUER,"OIDC_AUDIENCE","fresveg-supply","OIDC_JWK_SET_URI",TestIdentityProvider.jwksUri()));
            builder.environment().put("ACCOUNT_BASE_URL",TestAccountServer.url());
            builder.environment().put("INVENTORY_SERVICE_SUBJECTS","commerce-orders-test");
            builder.environment().put("CATALOG_BASE_URL",TestCatalogServer.url());
            builder.redirectErrorStream(true).redirectOutput(Path.of("target/test-supply.log").toFile()); process=builder.start();
            try (var client=HttpClient.newHttpClient()) {
                long deadline=System.nanoTime()+Duration.ofSeconds(40).toNanos();
                while (System.nanoTime()<deadline && process.isAlive()) {
                    try {
                        var response=client.send(HttpRequest.newBuilder(URI.create(address+"/actuator/health")).timeout(Duration.ofSeconds(1)).build(),HttpResponse.BodyHandlers.ofString());
                        if (response.statusCode()==200) { base=address; return base; }
                    } catch (java.io.IOException ignored) { }
                    Thread.sleep(100);
                }
            }
            throw new IllegalStateException("Supply test package did not start; see target/test-supply.log");
        } catch (Exception error) { stop(); throw new IllegalStateException("Cannot start isolated Supply service",error); }
    }
    static tools.jackson.databind.JsonNode mutate(String method,String path,String token,Object body) throws Exception {
        try(var client=HttpClient.newHttpClient()) {
            var mapper=new JsonMapper();var response=client.send(HttpRequest.newBuilder(URI.create(url()+"/api/v1/supply"+path)).timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+token).header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200 && response.statusCode()!=201)throw new IllegalStateException(response.body());return mapper.readTree(response.body()).path("data");
        }
    }
    static String tokenFile() {
        try {var p=Path.of("target/inventory-service-test-token");java.nio.file.Files.writeString(p,TestIdentityProvider.token("commerce-orders-test",c->c.claim("scope","inventory.reserve").expirationTime(java.util.Date.from(java.time.Instant.now().plusSeconds(3600))),false));return p.toAbsolutePath().toString();}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    static long activeReservations()throws Exception {try(var db=DatabaseFixture.admin(postgres)){return Long.parseLong(DatabaseFixture.scalar(db,"SELECT count(*) FROM supply.inventory_reservations WHERE status='ACTIVE'"));}}
    static long reservations() throws Exception {
        try(var db=DatabaseFixture.admin(postgres)) {return Long.parseLong(DatabaseFixture.scalar(db,"SELECT count(*) FROM supply.inventory_reservations"));}
    }
    static synchronized void stop() {
        if (process!=null) {
            process.destroy();
            try { if (!process.waitFor(5,java.util.concurrent.TimeUnit.SECONDS)) { process.destroyForcibly().waitFor(); } }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); process.destroyForcibly(); }
        }
        if (postgres!=null) { postgres.close(); }
        process=null; postgres=null; base=null;
    }
}
