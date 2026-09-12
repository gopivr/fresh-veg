package com.fresveg.account.api.dto;

import java.util.List;
import java.util.UUID;

public record AccountResponse(UUID userId, UUID customerId, String displayName, String email,
        String status, List<String> roles, Preferences preferences) {
    public record Preferences(String locale, String timeZone, boolean marketingOptIn) { }
}
