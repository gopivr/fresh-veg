package com.fresveg.common.http;

import java.util.UUID;
import java.util.regex.Pattern;

/** Transport-only request identifiers; never use these as identity or authorization. */
public final class CorrelationIds {
    public static final String HEADER = "X-Correlation-ID";
    public static final String CONTEXT_KEY = "correlationId";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    private CorrelationIds() {
    }

    public static String resolve(String supplied) {
        return supplied != null && SAFE_ID.matcher(supplied).matches()
                ? supplied : UUID.randomUUID().toString();
    }
}
