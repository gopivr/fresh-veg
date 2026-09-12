package com.fresveg.fulfillment.infrastructure.security;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
@Validated
@ConfigurationProperties("fulfillment.security")
public record FulfillmentSecurityProperties(@NotBlank @Size(max=512) @Pattern(regexp="https?://[^\\s]+") String issuerUri,@NotBlank @Size(max=255) String audience,@NotBlank @Pattern(regexp="https?://[^\\s]+") String jwkSetUri) {}
