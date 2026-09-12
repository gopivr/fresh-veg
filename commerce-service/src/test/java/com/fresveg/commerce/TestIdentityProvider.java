package com.fresveg.commerce;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Real signature/JWK verification with ephemeral test-only keys; no external IdP or fixed secret. */
final class TestIdentityProvider {
    static final String ISSUER = "https://issuer.example.test";
    private static final RSAKey KEY;
    private static final HttpServer SERVER;
    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
            SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            byte[] jwks = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            SERVER.createContext("/jwks", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jwks.length);
                try (var body = exchange.getResponseBody()) { body.write(jwks); }
            });
            SERVER.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> SERVER.stop(0)));
        } catch (Exception error) { throw new ExceptionInInitializerError(error); }
    }
    static String jwksUri() { return "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/jwks"; }
    static String token(String subject) throws Exception { return token(subject, claims -> {}, false); }
    static String token(String subject, Consumer<JWTClaimsSet.Builder> customize, boolean wrongKey) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(ISSUER).subject(subject).audience(List.of("fresveg-account", "fresveg-catalog", "fresveg-supply", "fresveg-commerce"))
                .issueTime(Date.from(Instant.now())).expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .claim("name", "Test Customer").claim("email", "customer@example.test").claim("email_verified", true)
                .claim("roles", List.of("PLATFORM_ADMIN")).claim("customerId", UUID.randomUUID().toString())
                .claim("vendorId", UUID.randomUUID().toString());
        customize.accept(claims);
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).type(JOSEObjectType.JWT).build(), claims.build());
        jwt.sign(new RSASSASigner(wrongKey ? new RSAKeyGenerator(2048).generate() : KEY));
        return jwt.serialize();
    }
}
