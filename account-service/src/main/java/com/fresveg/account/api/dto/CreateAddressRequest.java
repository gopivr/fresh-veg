package com.fresveg.account.api.dto;

import jakarta.validation.constraints.*;

public record CreateAddressRequest(
        @Size(max = 50) String label,
        @NotBlank @Size(max = 120) String recipientName,
        @NotBlank @Size(max = 200) String line1,
        @Size(max = 200) String line2,
        @NotBlank @Size(max = 100) String city,
        @Size(max = 100) String region,
        @NotBlank @Size(max = 20) String postalCode,
        @NotBlank @Size(max = 2) @Pattern(regexp = "[A-Z]{2}") String countryCode,
        @Size(max = 32) String phone) {
}
