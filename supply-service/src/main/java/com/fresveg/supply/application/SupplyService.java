package com.fresveg.supply.application;

import com.fresveg.common.cache.*;
import com.fresveg.supply.api.dto.*;
import com.fresveg.supply.domain.*;
import com.fresveg.supply.infrastructure.client.SupplyDependencies;
import com.fresveg.supply.infrastructure.persistence.*;
import com.fresveg.supply.infrastructure.security.SupplyAuthorization;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class SupplyService {
    private final VendorLocationRepository locations;
    private final VendorListingRepository listings;
    private final ListingPriceRepository prices;
    private final PriceTierRepository tiers;
    private final OfferRepository offers;
    private final SupplyAuthorization authorization;
    private final SupplyDependencies owners;
    private final SupplyMapper mapper;
    private final SupplyCursor cursors;
    private final SupplyJson json;
    private final PricingPolicy pricing;
    private final Clock clock;
    private final TtlCache cache;
    private final Duration cacheTtl;
    public SupplyService(VendorLocationRepository locations,VendorListingRepository listings,ListingPriceRepository prices,
            PriceTierRepository tiers,OfferRepository offers,SupplyAuthorization authorization,SupplyDependencies owners,
            SupplyMapper mapper,SupplyCursor cursors,SupplyJson json,PricingPolicy pricing,Clock clock,TtlCache cache,CacheProperties cacheProperties) {
        this.locations=locations;this.listings=listings;this.prices=prices;this.tiers=tiers;this.offers=offers;this.authorization=authorization;
        this.owners=owners;this.mapper=mapper;this.cursors=cursors;this.json=json;this.pricing=pricing;this.clock=clock;
        this.cache=cache;this.cacheTtl=cacheProperties.getTtl();
    }
    public Page<OfferResponse> offers(UUID product,UUID variant,String currency,BigDecimal quantity,int pageSize,String cursor) {
        pageSize(pageSize);pricing.currency(currency);pricing.quantity(quantity);
        var active=activeVariants(product);
        if (variant!=null) { active.keySet().retainAll(Set.of(variant)); }
        String filter=cursors.fingerprint(Arrays.asList("offers",product,variant,currency,quantity.stripTrailingZeros().toPlainString()));
        var position=cursors.decode(cursor,filter);Instant now=clock.instant();
        String key="supply:offers:"+filter+":"+pageSize+":"+Objects.toString(cursor,"");
        return cache.get(key,cacheTtl,() -> {
            var rows=offers.offers(product,active,currency,quantity,now,position==null?null:position.id(),pageSize+1);
            return page(rows,pageSize,filter,l -> offer(l,currency,quantity,now),VendorListing::getId);
        });
    }
    public ListingResponse listing(UUID id) {
        var listing=listings.findById(id).orElseThrow(() -> missing("Listing"));
        if (listing.getStatus()!=ListingStatus.ACTIVE || locations.findById(listing.getLocationId()).orElseThrow().getStatus()!=LocationStatus.ACTIVE) { throw missing("Listing"); }
        var active=activeVariants(listing.getProductId());
        if (!listing.getUomCode().equals(active.get(listing.getVariantId()))) { throw missing("Active listing variant"); }
        String key="supply:listing:"+id;
        return cache.get(key,cacheTtl,() -> {
            Instant now=clock.instant();var current=priceResponses(id).stream().filter(p -> pricing.effective(p,now)).toList();
            if (current.isEmpty()) { throw missing("Currently priced listing"); }
            return mapper.listing(listing,current);
        });
    }
    @PreAuthorize("isAuthenticated()")
    public Page<ListingSummary> vendorListings(UUID vendor,int pageSize,String cursor) {
        authorization.requireVendor(vendor,false);pageSize(pageSize);
        String filter=cursors.fingerprint(Arrays.asList("vendor-listings",vendor));var position=cursors.decode(cursor,filter);
        var rows=position==null?listings.findByVendorIdOrderByIdAsc(vendor,PageRequest.ofSize(pageSize+1))
                :listings.findByVendorIdAndIdGreaterThanOrderByIdAsc(vendor,position.id(),PageRequest.ofSize(pageSize+1));
        return page(rows,pageSize,filter,mapper::summary,VendorListing::getId);
    }
    @PreAuthorize("isAuthenticated()")
    public ListingResponse vendorListing(UUID id) { var listing=ownedListing(id,false);return mapper.listing(listing,priceResponses(id)); }
    @PreAuthorize("isAuthenticated()")
    public Page<LocationResponse> vendorLocations(UUID vendor,int pageSize,String cursor) {
        authorization.requireVendor(vendor,false);pageSize(pageSize);
        String filter=cursors.fingerprint(Arrays.asList("vendor-locations",vendor));var position=cursors.decode(cursor,filter);
        var rows=position==null?locations.findByVendorIdOrderByIdAsc(vendor,PageRequest.ofSize(pageSize+1))
                :locations.findByVendorIdAndIdGreaterThanOrderByIdAsc(vendor,position.id(),PageRequest.ofSize(pageSize+1));
        return page(rows,pageSize,filter,mapper::location,VendorLocation::getId);
    }
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public LocationResponse createLocation(UUID vendor,CreateLocationRequest request) {
        UUID actor=authorization.requireVendor(vendor,true);country(request.countryCode());
        var location=new VendorLocation(vendor,actor);replace(location,request,actor);
        var result=mapper.location(locations.saveAndFlush(location));
        cache.evictByPrefix("supply:");
        return result;
    }
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public LocationResponse updateLocation(UUID id,UpdateLocationRequest request) {
        var location=locations.findById(id).orElseThrow(() -> missing("Owned resource"));
        UUID actor=ownedActor(location.getVendorId(),true);country(request.countryCode());version(location.getVersion(),request.version());
        replace(location,request,actor);var result=mapper.location(locations.saveAndFlush(location));
        cache.evictByPrefix("supply:");
        return result;
    }
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ListingResponse createListing(UUID vendor,CreateListingRequest request) {
        UUID actor=authorization.requireVendor(vendor,true);validate(vendor,request,true);
        var listing=new VendorListing(vendor,request.locationId(),request.productId(),request.variantId(),request.uomCode(),actor);
        replace(listing,request,actor);listings.saveAndFlush(listing);replacePrices(listing,request.prices(),actor);
        var result=mapper.listing(listing,priceResponses(listing.getId()));
        cache.evictByPrefix("supply:");
        return result;
    }
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ListingResponse updateListing(UUID id,UpdateListingRequest request) {
        var listing=ownedListing(id,true);UUID actor=authorization.requireVendor(listing.getVendorId(),true);
        version(listing.getVersion(),request.version());
        if (!listing.getLocationId().equals(request.locationId()) || !listing.getProductId().equals(request.productId())
                || !listing.getVariantId().equals(request.variantId()) || !listing.getUomCode().equals(request.uomCode())) { throw invalid("Listing location, product, variant and UOM identity cannot change; create a new listing."); }
        validate(listing.getVendorId(),request,request.status()==ListingStatus.ACTIVE);
        replace(listing,request,actor);listings.saveAndFlush(listing);
        replacePrices(listing,request.prices(),actor);var result=mapper.listing(listing,priceResponses(id));
        cache.evictByPrefix("supply:");
        return result;
    }
    private VendorListing ownedListing(UUID id,boolean write) {
        var listing=listings.findById(id).orElseThrow(() -> missing("Owned resource"));ownedActor(listing.getVendorId(),write);return listing;
    }
    private UUID ownedActor(UUID vendor,boolean write) {
        var member=authorization.membership(vendor);
        if (!member.member()) { throw missing("Owned resource"); }
        if (write && !member.administrator()) { throw new AccessDeniedException("Vendor administrator authority is required"); }
        return member.userId();
    }
    private void validate(UUID vendor,ListingInput request,boolean validateActiveReferences) {
        json.attributes(request.attributes());pricing.validate(request.prices(),request.minimumOrderQuantity());
        var location=locations.findByIdAndVendorId(request.locationId(),vendor).orElseThrow(() -> invalid("Location must belong to the verified vendor."));
        if (validateActiveReferences) {
            if (location.getStatus()!=LocationStatus.ACTIVE) { throw invalid("An active vendor location is required."); }
            if (!request.uomCode().equals(activeVariants(request.productId()).get(request.variantId()))) { throw invalid("Variant and UOM must match an active Catalog variant."); }
        }
    }
    private Map<UUID,String> activeVariants(UUID product) {
        try {
            var response=owners.product(product);
            if (!product.toString().equals(response.path("productId").asString()) || !"ACTIVE".equals(response.path("status").asString()) || !response.path("variants").isArray()) { throw SupplyDependencies.unavailable(); }
            Map<UUID,String> result=new LinkedHashMap<>();
            for (var variant:response.path("variants")) {
                if ("ACTIVE".equals(variant.path("status").asString())) {
                    UUID id=UUID.fromString(variant.path("variantId").asString());String unit=variant.path("unitCode").asString();
                    if (!variant.path("unitCode").isString() || unit.isBlank() || unit.length()>16 || result.put(id,unit)!=null) { throw SupplyDependencies.unavailable(); }
                }
            }
            return result;
        } catch (SupplyException error) { throw error; }
        catch (Exception error) { throw SupplyDependencies.unavailable(); }
    }
    private void replacePrices(VendorListing listing,List<PriceRequest> requested,UUID actor) {
        var existing=prices.findByListingIdAndStatusOrderByValidFromAsc(listing.getId(),PriceStatus.ACTIVE)
                .stream().collect(Collectors.toMap(ListingPrice::getId,Function.identity()));
        for (var request:requested) {
            if (request.priceId()!=null) {
                var old=existing.remove(request.priceId());
                if (old==null || !samePrice(request,mapper.price(old,tiers.findByPriceIdOrderByMinQuantityAsc(old.getId())))) {
                    throw invalid("Existing price IDs must belong to this listing and keep their immutable schedule and tiers.");
                }
            }
        }
        existing.values().forEach(p -> p.cancel(actor));prices.flush();
        for (var request:requested) {
            if (request.priceId()==null) {
                var price=prices.saveAndFlush(new ListingPrice(listing.getId(),request.currency(),request.unitPrice(),request.minQuantity(),request.validFrom(),request.validTo(),actor));
                for (var tier:pricing.sorted(request.tiers())) { tiers.save(new PriceTier(price.getId(),tier.minQuantity(),tier.unitPrice(),actor)); }
            }
        }
        tiers.flush();
    }
    private boolean samePrice(PriceRequest request,PriceResponse old) {
        if (!request.currency().equals(old.currency()) || request.unitPrice().compareTo(old.unitPrice())!=0 || request.minQuantity().compareTo(old.minQuantity())!=0
                || !request.validFrom().equals(old.validFrom()) || !Objects.equals(request.validTo(),old.validTo()) || request.tiers().size()!=old.tiers().size()) { return false; }
        var sorted=pricing.sorted(request.tiers());
        for (int i=0;i<sorted.size();i++) {
            if (sorted.get(i).minQuantity().compareTo(old.tiers().get(i).minQuantity())!=0 || sorted.get(i).unitPrice().compareTo(old.tiers().get(i).unitPrice())!=0) { return false; }
        }
        return true;
    }
    private List<PriceResponse> priceResponses(UUID listing) {
        return prices.findByListingIdAndStatusOrderByValidFromAsc(listing,PriceStatus.ACTIVE).stream()
                .map(p -> mapper.price(p,tiers.findByPriceIdOrderByMinQuantityAsc(p.getId()))).toList();
    }
    private OfferResponse offer(VendorListing listing,String currency,BigDecimal quantity,Instant now) {
        var current=priceResponses(listing.getId()).stream().filter(p -> p.currency().equals(currency) && pricing.effective(p,now)).toList();
        if (current.size()!=1) { throw new SupplyException(HttpStatus.CONFLICT,"SUP-409-002","Offer pricing changed or is ambiguous; reload offers."); }
        var price=current.getFirst();var resolved=pricing.resolve(price,quantity);
        return new OfferResponse(listing.getId(),listing.getVendorId(),listing.getProductId(),listing.getVariantId(),listing.getUomCode(),listing.getMinimumOrderQuantity(),quantity,currency,
                resolved.unitPrice(),price.priceId(),resolved.tierId(),price.validFrom(),price.validTo(),now,listing.getVersion());
    }
    private void replace(VendorListing listing,ListingInput request,UUID actor) { listing.replace(request.vendorSku(),request.minimumOrderQuantity(),request.status(),request.attributes(),actor); }
    private void replace(VendorLocation location,LocationInput request,UUID actor) { location.replace(request.code(),request.name(),request.line1(),request.city(),request.postalCode(),request.countryCode(),request.status(),actor); }
    private static void country(String code) { if (!Set.of(Locale.getISOCountries()).contains(code)) { throw invalid("countryCode must be an ISO 3166-1 alpha-2 code."); } }
    private static void version(long actual,long requested) { if (actual!=requested) { throw new SupplyException(HttpStatus.CONFLICT,"SUP-409-001","The resource has changed; reload it before updating."); } }
    private static void pageSize(int size) { if (size<1 || size>100) { throw invalid("pageSize must be between 1 and 100."); } }
    private static SupplyException invalid(String message) { return new SupplyException(HttpStatus.BAD_REQUEST,"SUP-400-001",message); }
    private static SupplyException missing(String resource) { return new SupplyException(HttpStatus.NOT_FOUND,"SUP-404-001",resource+" not found."); }
    private <E,D> Page<D> page(List<E> rows,int size,String filter,Function<E,D> map,Function<E,UUID> id) {
        boolean more=rows.size()>size;var visible=rows.subList(0,Math.min(size,rows.size()));
        return new Page<>(visible.stream().map(map).toList(),more?cursors.encode(filter,"",id.apply(visible.getLast())):null,more);
    }
    public record Page<T>(List<T> data,String nextCursor,boolean hasNext) { }
}
