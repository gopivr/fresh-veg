package com.fresveg.account.application;

import com.fresveg.account.api.dto.*;
import com.fresveg.account.domain.*;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AccountMapper {
    public AccountResponse account(User user, CustomerProfile customer, UserPreference preference, List<String> roles) {
        return new AccountResponse(user.getId(), customer.getId(), user.getDisplayName(), user.getEmail(),
                user.getStatus().name(), List.copyOf(roles), new AccountResponse.Preferences(
                        preference.getLocale(), preference.getTimeZone(), preference.getMarketingOptIn()));
    }

    public AddressResponse address(Address address) {
        return new AddressResponse(address.getId(), address.getLabel(), address.getRecipientName(), address.getLine1(),
                address.getLine2(), address.getCity(), address.getRegion(), address.getPostalCode(),
                address.getCountryCode(), address.getPhone(), address.getVersion());
    }

    public VendorMembershipResponse membership(VendorUser membership) {
        Vendor vendor = membership.getVendor();
        return new VendorMembershipResponse(membership.getId(), vendor.getId(), vendor.getVendorCode(),
                vendor.getName(), membership.getMembershipRole().name());
    }
}
