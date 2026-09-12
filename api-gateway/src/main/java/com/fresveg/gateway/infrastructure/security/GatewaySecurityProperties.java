package com.fresveg.gateway.infrastructure.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "gateway.security")
public record GatewaySecurityProperties(
        @NotBlank @Size(max = 512) @Pattern(regexp = "https?://[^\\s]+") String issuerUri,
        @NotBlank @Size(max = 255) String audience,
        @NotBlank @Pattern(regexp = "https?://[^\\s]+") String jwkSetUri,
        List<String> allowedOrigins) {
}
