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
final class TestAccountServer {
    private static PostgreSQLContainer postgres;
    private static Process process;
    private static String base;
    static synchronized String url() {
        if (base!=null) { return base; }
        try {
            var jar=Path.of("../account-service/target/account-service-0.1.0-SNAPSHOT.jar").toAbsolutePath();
            if (!java.nio.file.Files.isRegularFile(jar)) { throw new IllegalStateException("Run the full Maven reactor to build the Account package first"); }
            postgres=DatabaseFixture.container(); postgres.start(); DatabaseFixture.bootstrap(postgres);
            int port;
            try (var socket=new ServerSocket(0,0,InetAddress.getLoopbackAddress())) { port=socket.getLocalPort(); }
            String address="http://127.0.0.1:"+port;
            var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                    "-Xmx256m","-XX:ActiveProcessorCount=2","-jar",jar.toString(),"--spring.profiles.active=default");
            builder.environment().putAll(java.util.Map.of("SERVER_PORT",String.valueOf(port),"SERVER_ADDRESS","127.0.0.1",
                    "DB_URL",postgres.getJdbcUrl(),"DB_PASSWORD",DatabaseFixture.PASSWORD,"MIGRATION_DB_PASSWORD",DatabaseFixture.PASSWORD,
                    "DB_USER","fresveg_account","MIGRATION_DB_USER","fresveg_account_migrator",
                    "OIDC_ISSUER_URI",TestIdentityProvider.ISSUER,"OIDC_AUDIENCE","fresveg-account","OIDC_JWK_SET_URI",TestIdentityProvider.jwksUri()));
            builder.redirectErrorStream(true).redirectOutput(Path.of("target/test-account.log").toFile()); process=builder.start();
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
            throw new IllegalStateException("Account test package did not start; see target/test-account.log");
        } catch (Exception error) { stop(); throw new IllegalStateException("Cannot start isolated Account service",error); }
    }
    static String user(String token, boolean admin) throws Exception {
        try (var client=HttpClient.newHttpClient()) {
            var response=client.send(HttpRequest.newBuilder(URI.create(url()+"/api/v1/accounts/me"))
                    .header("Authorization","Bearer "+token).timeout(Duration.ofSeconds(10)).build(),HttpResponse.BodyHandlers.ofString());
            if (response.statusCode()!=200) { throw new IllegalStateException(response.body()); }
            String user=new JsonMapper().readTree(response.body()).path("data").path("userId").asString();
            if (admin) {
                try (var connection=DatabaseFixture.admin(postgres); var sql=connection.prepareStatement(
                        "INSERT INTO account.user_roles(user_role_id,user_id,role_id,created_by,updated_by) SELECT gen_random_uuid(),?,role_id,?,? FROM account.roles WHERE code='PLATFORM_ADMIN' ON CONFLICT(user_id,role_id) DO NOTHING")) {
                    for (int i=1;i<=3;i++) { sql.setObject(i,UUID.fromString(user)); } sql.executeUpdate();
                }
            }
            return user;
        }
    }
    static void revokeAdmin(String user) throws Exception {
        try (var connection=DatabaseFixture.admin(postgres); var sql=connection.prepareStatement(
                "DELETE FROM account.user_roles WHERE user_id=? AND role_id=(SELECT role_id FROM account.roles WHERE code='PLATFORM_ADMIN')")) {
            sql.setObject(1,UUID.fromString(user)); sql.executeUpdate();
        }
    }
    static UUID vendor(String token,String role) throws Exception {
        String user=user(token,false);UUID vendor=UUID.randomUUID();
        try (var db=DatabaseFixture.admin(postgres);var sql=db.prepareStatement("INSERT INTO account.vendors(vendor_id,vendor_code,name) VALUES (?,?,'Test Vendor')")) {
            sql.setObject(1,vendor);sql.setString(2,vendor.toString());sql.executeUpdate();
        }
        membership(token,vendor,role);return vendor;
    }
    static void membership(String token,UUID vendor,String role) throws Exception {
        String user=user(token,false);
        try (var db=DatabaseFixture.admin(postgres);var sql=db.prepareStatement("INSERT INTO account.vendor_users(vendor_user_id,vendor_id,user_id,membership_role) VALUES (gen_random_uuid(),?,?,?)")) {
            sql.setObject(1,vendor);sql.setObject(2,UUID.fromString(user));sql.setString(3,role);sql.executeUpdate();
        }
    }
    static void revoke(String token,UUID vendor) throws Exception {
        String user=user(token,false);
        try (var db=DatabaseFixture.admin(postgres);var sql=db.prepareStatement("UPDATE account.vendor_users SET status='INACTIVE' WHERE user_id=? AND vendor_id=?")) {
            sql.setObject(1,UUID.fromString(user));sql.setObject(2,vendor);sql.executeUpdate();
        }
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
