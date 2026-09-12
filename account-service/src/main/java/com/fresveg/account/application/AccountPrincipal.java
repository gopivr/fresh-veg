package com.fresveg.account.application;

/** Identity key comes from a validated token, never a caller-supplied local UUID. */
public record AccountPrincipal(String issuer, String subject, String displayName, String verifiedEmail) { }
