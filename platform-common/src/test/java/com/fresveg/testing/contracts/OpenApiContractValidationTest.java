package com.fresveg.testing.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenApiContractValidationTest {
    private static final Path CONTRACT_DIR = Path.of("..", "contracts", "openapi");

    @Test
    void serviceContractsDeclareSharedPlatformConventions() throws Exception {
        for (String service : List.of("account", "catalog", "supply", "commerce", "fulfillment")) {
            Path contract = CONTRACT_DIR.resolve(service + "-api.yaml");
            assertThat(contract).exists();
            String yaml = Files.readString(contract);
            assertThat(yaml).contains("openapi: 3.1.0");
            assertThat(yaml).contains("/api/v1/");
            assertThat(yaml).contains("X-Correlation-ID");
            assertThat(yaml).contains("application/problem+json");
        }
    }

    @Test
    void commerceCheckoutContractDocumentsIdempotencyKey() throws Exception {
        String commerce = Files.readString(CONTRACT_DIR.resolve("commerce-api.yaml"));
        assertThat(commerce).contains("Idempotency-Key");
    }
}
