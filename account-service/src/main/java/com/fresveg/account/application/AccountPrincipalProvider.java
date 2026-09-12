package com.fresveg.account.application;

/** Supplies the externally validated identity without coupling application logic to JWT transport. */
public interface AccountPrincipalProvider {
    AccountPrincipal current();
}
