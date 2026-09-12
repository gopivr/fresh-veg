package com.fresveg.common.http;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CorrelationIdsTest {
    @Test
    void preservesSafeIdentifiers() {
        assertThat(CorrelationIds.resolve("request-42.example_1")).isEqualTo("request-42.example_1");
        assertThat(CorrelationIds.resolve("a".repeat(128))).hasSize(128);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "injected\r\nheader", "bad/id", "<script>"})
    void replacesMissingOrUnsafeValues(String value) {
        String resolved = CorrelationIds.resolve(value);
        assertThat(UUID.fromString(resolved).toString()).isEqualTo(resolved);
    }

    @Test
    void boundsLengthAndGeneratesIndependentIdentifiers() {
        assertThat(CorrelationIds.resolve("a".repeat(129))).hasSize(36);
        assertThat(CorrelationIds.resolve(null)).isNotEqualTo(CorrelationIds.resolve(null));
    }
}
