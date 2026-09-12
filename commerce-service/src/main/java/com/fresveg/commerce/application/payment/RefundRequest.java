package com.fresveg.commerce.application.payment;

import java.math.BigDecimal;
import java.util.UUID;

public record RefundRequest(UUID orderId,UUID paymentAttemptId,String providerReference,String currency,BigDecimal amount,String idempotencyKey) { }
