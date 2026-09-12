package com.fresveg.supply.application;

import static org.assertj.core.api.Assertions.*;
import com.fresveg.supply.api.dto.*;
import com.fresveg.supply.domain.PriceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class PricingPolicyTest {
    private final PricingPolicy policy=new PricingPolicy();
    private static final Instant NOW=Instant.parse("2026-09-08T12:00:00Z");
    @Test
    void validityIsInclusiveAtStartAndExclusiveAtEnd() {
        var price=new PriceResponse(UUID.randomUUID(),"USD",new BigDecimal("2.750001"),BigDecimal.ONE,NOW,NOW.plusSeconds(60),PriceStatus.ACTIVE,List.of());
        assertThat(policy.effective(price,NOW.minusNanos(1))).isFalse();
        assertThat(policy.effective(price,NOW)).isTrue();
        assertThat(policy.effective(price,NOW.plusSeconds(60).minusNanos(1))).isTrue();
        assertThat(policy.effective(price,NOW.plusSeconds(60))).isFalse();
    }
    @Test
    void exactThresholdChoosesLargestApplicableTierWithoutFloatingPointRounding() {
        UUID tier=UUID.randomUUID();
        var price=new PriceResponse(UUID.randomUUID(),"USD",new BigDecimal("2.750001"),BigDecimal.ONE,NOW,null,PriceStatus.ACTIVE,
                List.of(new TierResponse(tier,new BigDecimal("5"),new BigDecimal("2.125001"))));
        assertThat(policy.resolve(price,new BigDecimal("4.999999")).unitPrice()).isEqualByComparingTo("2.750001");
        assertThat(policy.resolve(price,new BigDecimal("5")).unitPrice()).isEqualByComparingTo("2.125001");
        assertThat(policy.resolve(price,new BigDecimal("5")).tierId()).isEqualTo(tier);
    }
    @Test
    void adjacentWindowsAreAllowedButOverlapsDuplicateThresholdsAndBulkIncreasesAreRejected() {
        policy.validate(List.of(price(NOW,NOW.plusSeconds(60),List.of()),price(NOW.plusSeconds(60),null,List.of())),BigDecimal.ONE);
        assertThatThrownBy(() -> policy.validate(List.of(price(NOW,null,List.of()),price(NOW.plusSeconds(60),null,List.of())),BigDecimal.ONE)).isInstanceOf(SupplyException.class);
        assertThatThrownBy(() -> policy.validate(List.of(price(NOW,null,List.of(new TierRequest(BigDecimal.TEN,new BigDecimal("3"))))),BigDecimal.ONE)).isInstanceOf(SupplyException.class);
        var tiers=List.of(new TierRequest(BigDecimal.TEN,BigDecimal.ONE),new TierRequest(BigDecimal.TEN,BigDecimal.ONE));
        assertThatThrownBy(() -> policy.validate(List.of(price(NOW,null,tiers)),BigDecimal.ONE)).isInstanceOf(SupplyException.class);
    }
    @Test
    void currenciesQuantityAndTimestampPrecisionHaveExplicitBounds() {
        assertThat(policy.currency("USD")).isEqualTo("USD");
        for (String bad:List.of("usd","ZZZ","XXX")) { assertThatThrownBy(() -> policy.currency(bad)).isInstanceOf(SupplyException.class); }
        for (String bad:List.of("0","-1","0.0000001","1000000000000")) { assertThatThrownBy(() -> policy.quantity(new BigDecimal(bad))).isInstanceOf(SupplyException.class); }
        assertThatThrownBy(() -> policy.validate(List.of(price(NOW.plusNanos(1),null,List.of())),BigDecimal.ONE)).isInstanceOf(SupplyException.class);
    }
    private static PriceRequest price(Instant from,Instant to,List<TierRequest> tiers) { return new PriceRequest(null,"USD",new BigDecimal("2"),BigDecimal.ONE,from,to,tiers); }
}
