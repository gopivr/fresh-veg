package com.fresveg.account.api;

import com.fresveg.common.http.CorrelationIds;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.*;

public final class AccountProblems {
    private AccountProblems() { }

    public static ProblemDetail create(HttpStatusCode status, String code, String detail, HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("about:blank"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("correlationId", requestId(request));
        return problem;
    }

    public static String requestId(HttpServletRequest request) {
        return request.getAttribute(CorrelationIds.CONTEXT_KEY) instanceof String id ? id : CorrelationIds.resolve(null);
    }
}
