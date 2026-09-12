package com.fresveg.supply.infrastructure.security;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
@Component("inventoryServiceAuthorization")
public class InventoryServiceAuthorization {
 private final Set<String> subjects;
 public InventoryServiceAuthorization(@Value("${supply.inventory.service-subjects:}") String configured) {
  subjects=new HashSet<>();for(String s:configured.split(",")) { if(!s.isBlank()) subjects.add(s.trim()); }
 }
 public String requireClient() {
  var auth=SecurityContextHolder.getContext().getAuthentication();
  if(auth instanceof JwtAuthenticationToken jwt && subjects.contains(jwt.getToken().getSubject()) && jwt.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("SCOPE_inventory.reserve"))) return jwt.getToken().getSubject();
  throw new AccessDeniedException("Configured service subject and inventory.reserve scope required");
 }
 public boolean allowed() { requireClient();return true; }
}
