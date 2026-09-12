package com.fresveg.supply.api.dto;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fresveg.supply.domain.*;
public record LocationResponse(UUID locationId,UUID vendorId,String code,String name,String line1,String city,String postalCode,String countryCode,LocationStatus status,long version) { }
