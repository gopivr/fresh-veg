package com.fresveg.catalog.api.dto;
import java.util.*;
import java.math.BigDecimal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fresveg.catalog.domain.*;
public record ImageRequest(@NotBlank @Size(max=2048) @Pattern(regexp="https://[^\\s]+") String url, @NotNull @Size(max=240) String altText) { }
