package com.fresveg.common.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/** Explicitly registered by servlet applications; not auto-configured or scanned. */
public final class ServletCorrelationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String id = CorrelationIds.resolve(request.getHeader(CorrelationIds.HEADER));
        String previous = MDC.get(CorrelationIds.CONTEXT_KEY);
        request.setAttribute(CorrelationIds.CONTEXT_KEY, id);
        response.setHeader(CorrelationIds.HEADER, id);
        MDC.put(CorrelationIds.CONTEXT_KEY, id);
        try {
            chain.doFilter(request, response);
        } finally {
            if (previous == null) {
                MDC.remove(CorrelationIds.CONTEXT_KEY);
            } else {
                MDC.put(CorrelationIds.CONTEXT_KEY, previous);
            }
        }
    }
}
