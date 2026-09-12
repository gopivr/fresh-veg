package com.fresveg.fulfillment.infrastructure.security;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.client.RestTemplate;
@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(FulfillmentSecurityProperties.class)
public class FulfillmentJwtConfiguration {
 @Bean JwtDecoder fulfillmentJwtDecoder(FulfillmentSecurityProperties p) {
  var req=new SimpleClientHttpRequestFactory();req.setConnectTimeout(Duration.ofSeconds(5));req.setReadTimeout(Duration.ofSeconds(5));
  var d=NimbusJwtDecoder.withJwkSetUri(p.jwkSetUri()).jwsAlgorithm(SignatureAlgorithm.RS256).restOperations(new RestTemplate(req)).build();
  OAuth2TokenValidator<Jwt> claims=jwt->{String s=jwt.getSubject();boolean ok=jwt.getExpiresAt()!=null&&s!=null&&!s.isBlank()&&s.length()<=255&&jwt.getAudience()!=null&&jwt.getAudience().contains(p.audience());return ok?OAuth2TokenValidatorResult.success():OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Required token claims are invalid",null));};
  d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(p.issuerUri()),claims));return d;
 }
}
