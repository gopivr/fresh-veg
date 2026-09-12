package com.fresveg.supply.application;

import com.fresveg.supply.api.dto.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PricingPolicy {
    public void validate(List<PriceRequest> prices,BigDecimal minimum) {
        var ids=new HashSet<UUID>();
        for (var price:prices) {
            currency(price.currency());
            if (price.priceId()!=null && !ids.add(price.priceId())) { throw invalid("Repeated price IDs are not allowed."); }
            if (price.minQuantity().compareTo(minimum)!=0) { throw invalid("Base price minQuantity must equal the listing minimumOrderQuantity."); }
            if (price.validFrom().getNano()%1000!=0 || (price.validTo()!=null && price.validTo().getNano()%1000!=0)) { throw invalid("Price timestamps support microsecond precision."); }
            if (price.validTo()!=null && !price.validTo().isAfter(price.validFrom())) { throw invalid("Price validTo must be after validFrom."); }
            BigDecimal threshold=price.minQuantity(), amount=price.unitPrice();
            for (var tier:sorted(price.tiers())) {
                if (tier.minQuantity().compareTo(threshold)<=0 || tier.unitPrice().compareTo(amount)>0) { throw invalid("Tier thresholds must increase and unit prices must not increase."); }
                threshold=tier.minQuantity();amount=tier.unitPrice();
            }
        }
        for (int i=0;i<prices.size();i++) {
            for (int j=i+1;j<prices.size();j++) {
                var a=prices.get(i);var b=prices.get(j);
                if (a.currency().equals(b.currency()) && (a.validTo()==null || b.validFrom().isBefore(a.validTo())) && (b.validTo()==null || a.validFrom().isBefore(b.validTo()))) {
                    throw invalid("Price validity windows may not overlap within a currency.");
                }
            }
        }
    }
    public String currency(String code) {
        try {
            if (code==null || !code.matches("[A-Z]{3}") || Currency.getInstance(code).getDefaultFractionDigits()<0) { throw new IllegalArgumentException(); }
            return code;
        } catch (IllegalArgumentException error) { throw invalid("A supported ISO-4217 currency is required."); }
    }
    public BigDecimal quantity(BigDecimal quantity) {
        if (quantity==null || quantity.signum()<=0 || quantity.scale()>6 || quantity.precision()-quantity.scale()>12) { throw invalid("Quantity must be positive with at most twelve integer and six fractional digits."); }
        return quantity;
    }
    public boolean effective(PriceResponse price,Instant now) {
        return price.status()==com.fresveg.supply.domain.PriceStatus.ACTIVE && !now.isBefore(price.validFrom()) && (price.validTo()==null || now.isBefore(price.validTo()));
    }
    public Resolved resolve(PriceResponse price,BigDecimal quantity) {
        if (quantity.compareTo(price.minQuantity())<0) { throw invalid("Quantity is below the price minimum."); }
        BigDecimal amount=price.unitPrice();UUID tierId=null;
        for (var tier:price.tiers()) {
            if (quantity.compareTo(tier.minQuantity())>=0) { amount=tier.unitPrice();tierId=tier.tierId(); }
        }
        return new Resolved(amount,tierId);
    }
    public List<TierRequest> sorted(List<TierRequest> tiers) { return tiers.stream().sorted(Comparator.comparing(TierRequest::minQuantity)).toList(); }
    private static SupplyException invalid(String message) { return new SupplyException(HttpStatus.BAD_REQUEST,"SUP-400-004",message); }
    public record Resolved(BigDecimal unitPrice,UUID tierId) { }
}
