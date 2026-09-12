package com.fresveg.common.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ServletCorrelationFilterTest {
    @Test
    void exposesCorrelationDuringRequestAndCleansThreadContext() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(CorrelationIds.HEADER, "test-request");
        var response = new MockHttpServletResponse();
        new ServletCorrelationFilter().doFilter(request, response, (req, res) -> {
            assertThat(req.getAttribute(CorrelationIds.CONTEXT_KEY)).isEqualTo("test-request");
            assertThat(MDC.get(CorrelationIds.CONTEXT_KEY)).isEqualTo("test-request");
        });
        assertThat(response.getHeader(CorrelationIds.HEADER)).isEqualTo("test-request");
        assertThat(MDC.get(CorrelationIds.CONTEXT_KEY)).isNull();
    }

    @Test
    void restoresPreviousContextEvenWhenDownstreamFails() {
        MDC.put(CorrelationIds.CONTEXT_KEY, "outer-request");
        try {
            assertThatThrownBy(() -> new ServletCorrelationFilter().doFilter(
                    new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> {
                        throw new ServletException("expected test failure");
                    })).isInstanceOf(ServletException.class);
            assertThat(MDC.get(CorrelationIds.CONTEXT_KEY)).isEqualTo("outer-request");
        } finally {
            MDC.clear();
        }
    }
}
