package com.fresveg.supply.infrastructure.security;
import static org.assertj.core.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
class InventoryServiceAuthorizationTest {
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 @Test void serviceNeedsBothExplicitSubjectAndScope(){
  var policy=new InventoryServiceAuthorization(" commerce , warehouse ");token("commerce",true);assertThat(policy.requireClient()).isEqualTo("commerce");
  token("commerce",false);assertThatThrownBy(policy::requireClient).isInstanceOf(AccessDeniedException.class);
  token("customer",true);assertThatThrownBy(policy::requireClient).isInstanceOf(AccessDeniedException.class);
 }
 @Test void emptyConfigurationAndAnonymousFailClosed(){
  var policy=new InventoryServiceAuthorization("");assertThatThrownBy(policy::requireClient).isInstanceOf(AccessDeniedException.class);
  token("commerce",true);assertThatThrownBy(policy::requireClient).isInstanceOf(AccessDeniedException.class);
 }
 private void token(String subject,boolean scope){
  var jwt=Jwt.withTokenValue("validated-test-fixture").header("alg","RS256").subject(subject).build();
  SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,scope?List.of(new SimpleGrantedAuthority("SCOPE_inventory.reserve")):List.of()));
 }
}
