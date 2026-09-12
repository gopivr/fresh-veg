package com.fresveg.commerce.infrastructure.payment;

import com.fresveg.commerce.application.payment.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalPaymentGateway implements PaymentGateway {
 private final Set<UUID> declined,refundFailures;
 public LocalPaymentGateway(@Value("${commerce.payments.local.declined-method-ids:}") String declined,@Value("${commerce.payments.local.refund-failure-attempt-ids:}") String refundFailures) {
  this.declined=parse(declined);this.refundFailures=parse(refundFailures);
 }
 @Override public PaymentAuthorizationResult authorize(PaymentAuthorizationRequest request) {
  if(declined.contains(request.paymentMethodId()))return new PaymentAuthorizationResult(false,"local","local-auth-"+request.orderId(),"LOCAL_DECLINED","Payment method was declined.");
  return new PaymentAuthorizationResult(true,"local","local-auth-"+request.orderId(),null,null);
 }
 @Override public RefundResult refund(RefundRequest request) {
  if(refundFailures.contains(request.paymentAttemptId()))return new RefundResult(false,"local","local-refund-"+request.orderId(),"LOCAL_REFUND_FAILED","Refund could not be completed.");
  return new RefundResult(true,"local","local-refund-"+request.orderId(),null,null);
 }
 private static Set<UUID> parse(String value) {
  if(value==null || value.isBlank())return Set.of();
  var result=new LinkedHashSet<UUID>();for(var part:value.split(","))if(!part.isBlank())result.add(UUID.fromString(part.strip()));
  return Set.copyOf(result);
 }
}
