package com.fresveg.fulfillment;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
final class TestIdentityProvider {
 static final String ISSUER="https://issuer.example.test";private static final RSAKey KEY;private static final HttpServer SERVER;
 static{try{KEY=new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();SERVER=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);byte[] jwks=new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);SERVER.createContext("/jwks",e->{e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,jwks.length);try(var b=e.getResponseBody()){b.write(jwks);}});SERVER.start();Runtime.getRuntime().addShutdownHook(new Thread(()->SERVER.stop(0)));}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 static String jwksUri(){return "http://127.0.0.1:"+SERVER.getAddress().getPort()+"/jwks";}
 static String token(String sub)throws Exception{return token(sub,c->{},false);}
 static String token(String sub,Consumer<JWTClaimsSet.Builder> custom,boolean wrongKey)throws Exception{var claims=new JWTClaimsSet.Builder().issuer(ISSUER).subject(sub).audience(List.of("fresveg-fulfillment")).issueTime(Date.from(Instant.now())).expirationTime(Date.from(Instant.now().plusSeconds(3600)));custom.accept(claims);var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).type(JOSEObjectType.JWT).build(),claims.build());jwt.sign(new RSASSASigner(wrongKey?new RSAKeyGenerator(2048).generate():KEY));return jwt.serialize();}
}
