package com.fresveg.fulfillment.api;
import com.fresveg.common.http.CorrelationIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
public final class FulfillmentProblems {
 private FulfillmentProblems() {}
 public static ProblemDetail create(HttpStatusCode status,String code,String detail,HttpServletRequest request) {
  var p=ProblemDetail.forStatusAndDetail(status,detail);p.setType(java.net.URI.create("https://api.fresveg.example/problems/"+code));p.setTitle(HttpStatus.valueOf(status.value()).getReasonPhrase());p.setProperty("code",code);p.setProperty("correlationId",requestId(request));return p;
 }
 public static String requestId(HttpServletRequest r) {return r.getAttribute(CorrelationIds.CONTEXT_KEY) instanceof String id?id:CorrelationIds.resolve(null);}
}
