package com.fresveg.commerce.application.payment;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentAuthorizationRequest(UUID orderId,UUID customerId,UUID paymentMethodId,String currency,BigDecimal amount,String idempotencyKey) { }
