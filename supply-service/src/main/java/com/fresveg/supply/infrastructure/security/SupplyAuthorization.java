package com.fresveg.supply.infrastructure.security;

import com.fresveg.supply.infrastructure.client.SupplyDependencies;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.*;

@Component
public class SupplyAuthorization {
    private final SupplyDependencies owners;
    public SupplyAuthorization(SupplyDependencies owners) { this.owners=owners; }
    public Membership membership(UUID vendor) {
        String cacheKey=SupplyAuthorization.class.getName()+vendor;
        var request=RequestContextHolder.getRequestAttributes();
        if (request instanceof ServletRequestAttributes attributes && attributes.getRequest().getAttribute(cacheKey) instanceof Membership cached) { return cached; }
        try {
            var me=owners.account("/api/v1/accounts/me").path("data");
            if (!"ACTIVE".equals(me.path("status").asString())) { throw new AccessDeniedException("Active Account identity is required"); }
            UUID user=UUID.fromString(me.path("userId").asString());
            String cursor=null;var seen=new HashSet<String>();
            long deadline=System.nanoTime()+java.time.Duration.ofSeconds(10).toNanos();
            for (int page=0;page<100;page++) {
                if (System.nanoTime()>deadline) { throw SupplyDependencies.unavailable(); }
                var response=owners.account("/api/v1/accounts/me/vendor-memberships?pageSize=100"+(cursor==null?"":"&cursor="+cursor));
                if (!response.path("data").isArray() || !response.path("pagination").path("hasNext").isBoolean()) { throw SupplyDependencies.unavailable(); }
                for (var row:response.path("data")) {
                    UUID vendorId=UUID.fromString(row.path("vendorId").asString());
                    String role=row.path("role").asString();
                    if (!Set.of("VENDOR_ADMIN","VENDOR_STAFF").contains(role)) { throw SupplyDependencies.unavailable(); }
                    if (vendorId.equals(vendor)) {
                        var result=new Membership(user,true,"VENDOR_ADMIN".equals(role));
                        if (request instanceof ServletRequestAttributes attributes) { attributes.getRequest().setAttribute(cacheKey,result); }
                        return result;
                    }
                }
                if (!response.path("pagination").path("hasNext").asBoolean()) { return new Membership(user,false,false); }
                cursor=UUID.fromString(response.path("pagination").path("nextCursor").asString()).toString();
                if (!seen.add(cursor)) { throw SupplyDependencies.unavailable(); }
            }
            throw SupplyDependencies.unavailable();
        } catch (AccessDeniedException | com.fresveg.supply.application.SupplyException error) { throw error; }
        catch (Exception error) { throw SupplyDependencies.unavailable(); }
    }
    public UUID requireVendor(UUID vendor,boolean write) {
        var membership=membership(vendor);
        if (!membership.member() || (write && !membership.administrator())) { throw new AccessDeniedException("Verified vendor membership with the required role is needed"); }
        return membership.userId();
    }
    public record Membership(UUID userId,boolean member,boolean administrator) { }
}
