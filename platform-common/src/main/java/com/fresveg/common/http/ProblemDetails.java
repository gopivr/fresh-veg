package com.fresveg.common.http;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Infrastructure error contract, without domain or authorization policy. */
public final class ProblemDetails {
    private ProblemDetails() {
    }

    public static ProblemDetail forbidden(String correlationId) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access to this resource is denied.");
        problem.setType(URI.create("about:blank"));
        problem.setProperty("code", "SEC-403-001");
        problem.setProperty(CorrelationIds.CONTEXT_KEY, correlationId);
        return problem;
    }
}
