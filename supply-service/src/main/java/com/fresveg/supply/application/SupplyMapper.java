package com.fresveg.supply.application;

import com.fresveg.supply.api.dto.*;
import com.fresveg.supply.domain.*;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SupplyMapper {
    public LocationResponse location(VendorLocation l) { return new LocationResponse(l.getId(),l.getVendorId(),l.getCode(),l.getName(),l.getLine1(),l.getCity(),l.getPostalCode(),l.getCountryCode(),l.getStatus(),l.getVersion()); }
    public ListingSummary summary(VendorListing l) { return new ListingSummary(l.getId(),l.getVendorId(),l.getLocationId(),l.getProductId(),l.getVariantId(),l.getVendorSku(),l.getUomCode(),l.getMinimumOrderQuantity(),l.getStatus(),l.getVersion()); }
    public ListingResponse listing(VendorListing l,List<PriceResponse> prices) { return new ListingResponse(l.getId(),l.getVendorId(),l.getLocationId(),l.getProductId(),l.getVariantId(),l.getVendorSku(),l.getUomCode(),l.getMinimumOrderQuantity(),l.getStatus(),l.getAttributes(),prices,l.getVersion()); }
    public PriceResponse price(ListingPrice p,List<PriceTier> tiers) {
        return new PriceResponse(p.getId(),p.getCurrency(),p.getUnitPrice(),p.getMinQuantity(),p.getValidFrom(),p.getValidTo(),p.getStatus(),
                tiers.stream().map(t -> new TierResponse(t.getId(),t.getMinQuantity(),t.getUnitPrice())).toList());
    }
}
