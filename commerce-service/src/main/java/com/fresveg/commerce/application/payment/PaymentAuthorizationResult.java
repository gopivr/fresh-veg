package com.fresveg.commerce.application.payment;

public record PaymentAuthorizationResult(boolean authorized,String provider,String providerReference,String failureCode,String failureMessage) { }
