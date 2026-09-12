package com.fresveg.fulfillment.application;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
@Component("fulfillmentServiceAuthorization")
public class FulfillmentServiceAuthorization {
 private final Set<String> subjects;
 public FulfillmentServiceAuthorization(@Value("${fulfillment.service-subjects:}") String subjects){this.subjects=parse(subjects);}
 public boolean allowed(){try{return subjects.contains(subject());}catch(RuntimeException e){return false;}}
 public UUID actor(){try{return UUID.fromString(subject());}catch(Exception e){return null;}}
 private String subject(){var a=SecurityContextHolder.getContext().getAuthentication();if(!(a instanceof JwtAuthenticationToken t)||!t.isAuthenticated())throw new AccessDeniedException("Service token required");String s=t.getToken().getSubject();if(s==null||s.isBlank())throw new AccessDeniedException("Service token required");return s;}
 private static Set<String> parse(String v){if(v==null||v.isBlank())return Set.of();var r=new LinkedHashSet<String>();for(var p:v.split(","))if(!p.isBlank())r.add(p.strip());return Set.copyOf(r);}
}
