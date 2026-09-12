package com.fresveg.account.api.dto;

import java.util.UUID;

public record AddressResponse(UUID addressId, String label, String recipientName, String line1,
        String line2, String city, String region, String postalCode, String countryCode, String phone, long version) { }
