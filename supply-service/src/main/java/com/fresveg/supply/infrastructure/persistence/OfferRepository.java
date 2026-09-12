package com.fresveg.supply.infrastructure.persistence;

import com.fresveg.supply.domain.VendorListing;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class OfferRepository {
    private final EntityManager entities;
    public OfferRepository(EntityManager entities) { this.entities=entities; }
    @SuppressWarnings("unchecked")
    public List<VendorListing> offers(UUID product,Map<UUID,String> variants,String currency,BigDecimal quantity,Instant now,UUID cursor,int limit) {
        if (variants.isEmpty()) { return List.of(); }
        String sql="""
                SELECT l.* FROM supply.vendor_listings l
                JOIN supply.vendor_locations v ON v.location_id=l.location_id AND v.vendor_id=l.vendor_id
                WHERE l.product_id=:product AND l.status='ACTIVE' AND v.status='ACTIVE'
                  AND l.minimum_order_quantity<=:quantity
                  AND EXISTS (SELECT 1 FROM supply.vendor_listing_prices p WHERE p.listing_id=l.listing_id
                    AND p.status='ACTIVE' AND p.currency=:currency AND p.min_quantity<=:quantity
                    AND p.valid_from<=:now AND (p.valid_to IS NULL OR p.valid_to>:now))
                """;
        var entries=new ArrayList<>(variants.entrySet());
        var conditions=new ArrayList<String>();
        for (int i=0;i<entries.size();i++) { conditions.add("(l.variant_id=:variant"+i+" AND l.uom_code=:unit"+i+")"); }
        sql+=" AND ("+String.join(" OR ",conditions)+")";
        if (cursor!=null) { sql+=" AND l.listing_id>:cursor"; }
        sql+=" ORDER BY l.listing_id";
        var query=entities.createNativeQuery(sql,VendorListing.class).unwrap(org.hibernate.query.NativeQuery.class);
        query.setParameter("product",product);query.setParameter("currency",currency);
        query.setParameter("quantity",quantity);query.setParameter("now",java.sql.Timestamp.from(now));
        if (cursor!=null) { query.setParameter("cursor",cursor); }
        for (int i=0;i<entries.size();i++) { query.setParameter("variant"+i,entries.get(i).getKey());query.setParameter("unit"+i,entries.get(i).getValue()); }
        query.setMaxResults(limit);return query.getResultList();
    }
}
