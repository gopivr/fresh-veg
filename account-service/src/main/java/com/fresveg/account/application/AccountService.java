package com.fresveg.account.application;

import com.fresveg.account.api.dto.*;
import com.fresveg.account.domain.*;
import com.fresveg.account.infrastructure.persistence.*;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class AccountService {
    private static final Set<String> COUNTRIES = Set.of(Locale.getISOCountries());
    private final AccountIdentityService identity;
    private final CustomerProfileRepository customers;
    private final UserPreferenceRepository preferences;
    private final RoleRepository roles;
    private final AddressRepository addresses;
    private final VendorMembershipRepository memberships;
    private final AccountMapper mapper;

    public AccountService(AccountIdentityService identity, CustomerProfileRepository customers,
            UserPreferenceRepository preferences, RoleRepository roles, AddressRepository addresses,
            VendorMembershipRepository memberships, AccountMapper mapper) {
        this.identity = identity;
        this.customers = customers;
        this.preferences = preferences;
        this.roles = roles;
        this.addresses = addresses;
        this.memberships = memberships;
        this.mapper = mapper;
    }

    public AccountResponse me() {
        User user = identity.currentUser();
        return mapper.account(user, customer(user), preferences.findByUserId(user.getId()).orElseThrow(), roles.codesForUser(user.getId()));
    }

    public Page<AddressResponse> addresses(int pageSize, UUID cursor) {
        validatePageSize(pageSize);
        UUID customerId = customer(identity.currentUser()).getId();
        var limit = PageRequest.ofSize(pageSize + 1);
        var rows = cursor == null ? addresses.findByCustomerIdOrderByIdAsc(customerId, limit)
                : addresses.findByCustomerIdAndIdGreaterThanOrderByIdAsc(customerId, cursor, limit);
        return page(rows, pageSize, mapper::address, Address::getId);
    }

    public AddressResponse createAddress(CreateAddressRequest request) {
        validateCountry(request.countryCode());
        User user = identity.currentUser();
        var address = new Address(customer(user).getId(), user.getId());
        address.replace(request.label(), request.recipientName(), request.line1(), request.line2(), request.city(),
                request.region(), request.postalCode(), request.countryCode(), request.phone(), user.getId());
        return mapper.address(addresses.saveAndFlush(address));
    }

    public AddressResponse updateAddress(UUID addressId, UpdateAddressRequest request) {
        validateCountry(request.countryCode());
        User user = identity.currentUser();
        UUID customerId = customer(user).getId();
        var address = addresses.findByIdAndCustomerId(addressId, customerId).orElseThrow(() ->
                new AccountException(HttpStatus.NOT_FOUND, "ACC-404-001", "Address not found."));
        if (request.version() != address.getVersion()) {
            throw new AccountException(HttpStatus.CONFLICT, "ACC-409-001", "The address has changed; reload it before updating.");
        }
        address.replace(request.label(), request.recipientName(), request.line1(), request.line2(), request.city(),
                request.region(), request.postalCode(), request.countryCode(), request.phone(), user.getId());
        return mapper.address(addresses.saveAndFlush(address));
    }

    public Page<VendorMembershipResponse> memberships(int pageSize, UUID cursor) {
        validatePageSize(pageSize);
        UUID userId = identity.currentUser().getId();
        var limit = PageRequest.ofSize(pageSize + 1);
        var rows = cursor == null ? memberships.active(userId, limit) : memberships.activeAfter(userId, cursor, limit);
        return page(rows, pageSize, mapper::membership, VendorUser::getId);
    }

    private CustomerProfile customer(User user) {
        var customer = customers.findByUserId(user.getId()).orElseThrow(() ->
                new AccountException(HttpStatus.CONFLICT, "ACC-409-002", "A customer profile is not available for this account."));
        if (customer.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountException(HttpStatus.FORBIDDEN, "ACC-403-002", "This customer profile is not active.");
        }
        return customer;
    }

    private static void validateCountry(String country) {
        if (!COUNTRIES.contains(country)) {
            throw new AccountException(HttpStatus.BAD_REQUEST, "ACC-400-001", "countryCode must be an ISO 3166-1 alpha-2 country code.");
        }
    }

    private static void validatePageSize(int pageSize) {
        if (pageSize < 1 || pageSize > 100) {
            throw new AccountException(HttpStatus.BAD_REQUEST, "ACC-400-002", "pageSize must be between 1 and 100.");
        }
    }

    private static <E, D> Page<D> page(List<E> rows, int pageSize, Function<E, D> map, Function<E, UUID> id) {
        boolean hasNext = rows.size() > pageSize;
        var visible = rows.subList(0, Math.min(rows.size(), pageSize));
        return new Page<>(visible.stream().map(map).toList(), hasNext ? id.apply(visible.getLast()) : null, hasNext);
    }

    public record Page<T>(List<T> data, UUID nextCursor, boolean hasNext) { }
}
