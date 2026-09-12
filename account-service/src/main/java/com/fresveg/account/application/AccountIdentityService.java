package com.fresveg.account.application;

import com.fresveg.account.domain.*;
import com.fresveg.account.infrastructure.persistence.UserRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountIdentityService {
    private final UserRepository users;
    private final AccountPrincipalProvider principalProvider;

    public AccountIdentityService(UserRepository users, AccountPrincipalProvider principalProvider) {
        this.users = users;
        this.principalProvider = principalProvider;
    }

    @Transactional
    public User currentUser() {
        AccountPrincipal principal = principalProvider.current();
        var existing = users.findByOidcIssuerAndOidcSubject(principal.issuer(), principal.subject());
        User user;
        if (existing.isPresent()) {
            user = existing.get();
        } else {
            UUID id = UUID.randomUUID();
            // Concurrent first requests serialize on the unique issuer/subject pair.
            // Only the winning transaction creates defaults; everything commits together.
            if (users.provision(id, principal.issuer(), principal.subject(), principal.displayName(), principal.verifiedEmail()) == 1) {
                users.createCustomer(id, principal.displayName());
                users.createPreferences(id);
                users.assignCustomerRole(id);
            }
            user = users.findByOidcIssuerAndOidcSubject(principal.issuer(), principal.subject()).orElseThrow();
        }
        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountException(HttpStatus.FORBIDDEN, "ACC-403-001", "This account is not active.");
        }
        return user;
    }
}
