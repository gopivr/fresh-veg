package com.fresveg.commerce.application.payment;

public record RefundResult(boolean completed,String provider,String providerReference,String failureCode,String failureMessage) { }
