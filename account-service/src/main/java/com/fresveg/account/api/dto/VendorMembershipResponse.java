package com.fresveg.account.api.dto;

import java.util.UUID;

public record VendorMembershipResponse(UUID membershipId, UUID vendorId, String vendorCode,
        String vendorName, String role) { }
