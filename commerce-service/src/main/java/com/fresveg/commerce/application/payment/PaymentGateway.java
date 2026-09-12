package com.fresveg.commerce.application.payment;

public interface PaymentGateway {
 PaymentAuthorizationResult authorize(PaymentAuthorizationRequest request);
 RefundResult refund(RefundRequest request);
}
