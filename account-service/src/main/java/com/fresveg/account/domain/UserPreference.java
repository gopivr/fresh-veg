package com.fresveg.account.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "user_preferences", schema = "account")
@AttributeOverride(name = "id", column = @Column(name = "preference_id"))
public class UserPreference extends AuditedEntity {
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "locale", nullable = true, length = 35)
    private String locale;
    @Column(name = "time_zone", nullable = true, length = 64)
    private String timeZone;
    @Column(name = "marketing_opt_in", nullable = false)
    private boolean marketingOptIn;

    protected UserPreference() { }
    public UUID getUserId() { return userId; }
    public String getLocale() { return locale; }
    public String getTimeZone() { return timeZone; }
    public boolean getMarketingOptIn() { return marketingOptIn; }
}
