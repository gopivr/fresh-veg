package com.fresveg.account.api;

import com.fresveg.account.api.dto.*;
import com.fresveg.account.application.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1/accounts/me", produces = "application/json")
@Tag(name = "Account")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {
    private final AccountService accounts;

    public AccountController(AccountService accounts) { this.accounts = accounts; }

    @GetMapping
    @Operation(operationId = "getMyAccount", summary = "Read the account for the authenticated identity")
    public ApiResponse<AccountResponse> me(HttpServletRequest request) {
        return new ApiResponse<>(accounts.me(), meta(request));
    }

    @GetMapping("/addresses")
    @Operation(operationId = "listMyAddresses", summary = "List the authenticated customer's addresses")
    public CollectionResponse<AddressResponse> addresses(@RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) UUID cursor, HttpServletRequest request) {
        var page = accounts.addresses(pageSize, cursor);
        return new CollectionResponse<>(page.data(), new CollectionResponse.Pagination(page.nextCursor(), page.hasNext()), meta(request));
    }

    @PostMapping(value = "/addresses", consumes = "application/json")
    @Operation(operationId = "createMyAddress", summary = "Create an address owned by the authenticated customer")
    public ResponseEntity<ApiResponse<AddressResponse>> create(@Valid @RequestBody CreateAddressRequest body,
            HttpServletRequest request) {
        var address = accounts.createAddress(body);
        return ResponseEntity.created(URI.create("/api/v1/accounts/me/addresses/" + address.addressId()))
                .body(new ApiResponse<>(address, meta(request)));
    }

    @PutMapping(value = "/addresses/{addressId}", consumes = "application/json")
    @Operation(operationId = "replaceMyAddress", summary = "Replace an owned address using its current version")
    public ApiResponse<AddressResponse> update(@PathVariable UUID addressId, @Valid @RequestBody UpdateAddressRequest body,
            HttpServletRequest request) {
        return new ApiResponse<>(accounts.updateAddress(addressId, body), meta(request));
    }

    @GetMapping("/vendor-memberships")
    @Operation(operationId = "listMyVendorMemberships", summary = "List active stored memberships in active vendors")
    public CollectionResponse<VendorMembershipResponse> memberships(@RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) UUID cursor, HttpServletRequest request) {
        var page = accounts.memberships(pageSize, cursor);
        return new CollectionResponse<>(page.data(), new CollectionResponse.Pagination(page.nextCursor(), page.hasNext()), meta(request));
    }

    private static ApiResponse.Meta meta(HttpServletRequest request) {
        return new ApiResponse.Meta(AccountProblems.requestId(request), Instant.now());
    }
}
