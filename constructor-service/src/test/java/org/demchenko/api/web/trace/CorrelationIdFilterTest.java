package org.demchenko.api.web.trace;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void keepsTraceOnlyForTheCurrentRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "request-one");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> valueInsideChain = new AtomicReference<>();
        FilterChain chain = (ignoredRequest, ignoredResponse) ->
                valueInsideChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        assertThat(valueInsideChain).hasValue("request-one");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo("request-one");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void rejectsUnsafeClientCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "unsafe\nlog-entry");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> { });

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME))
                .isNotBlank()
                .doesNotContain("unsafe", "\n");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
