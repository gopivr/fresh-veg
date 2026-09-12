package com.fresveg.catalog.api.dto;
import java.util.*;
import java.math.BigDecimal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fresveg.catalog.domain.*;
public record CreateCategoryRequest(@NotBlank @Pattern(regexp="[A-Z0-9][A-Z0-9_-]{0,63}") String code, @NotBlank @Size(max=160) String name, UUID parentCategoryId) { }
