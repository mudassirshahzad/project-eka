package com.mudassirshahzad.eka.api.observability;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit coverage for {@link CorrelationIdFilter} (P05.4, ADR OB03): ID generation vs. propagation,
 * MDC population during the downstream call and cleanup afterward, response-header echo, and
 * rejection of unsafe caller-supplied IDs (log-injection guard).
 */
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void noIncomingHeader_generatesIdAndSetsResponseHeaderAndMdc() throws Exception {
        MockHttpServletRequest  request  = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        String[] mdcDuringChain = new String[1];
        doAnswer(inv -> {
            mdcDuringChain[0] = MDC.get("correlationId");
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilter(request, response, filterChain);

        String header = response.getHeader("X-Correlation-Id");
        assertThat(header).isNotBlank();
        assertThat(mdcDuringChain[0]).isEqualTo(header);
        assertThat(MDC.get("correlationId")).isNull(); // cleared after the chain completes
    }

    @Test
    void safeIncomingHeader_isReused() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-Id", "client-supplied-id-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo("client-supplied-id-123");
    }

    @Test
    void unsafeIncomingHeader_isReplacedWithGeneratedId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-Id", "line1\r\nInjected: forged-log-line");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        String header = response.getHeader("X-Correlation-Id");
        assertThat(header).doesNotContain("\r", "\n", "Injected");
    }

    @Test
    void overlongIncomingHeader_isReplacedWithGeneratedId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-Id", "a".repeat(500));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getHeader("X-Correlation-Id")).hasSizeLessThan(500);
    }

    @Test
    void mdcClearedEvenWhenDownstreamThrows() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        org.mockito.Mockito.doAnswer(inv -> { throw new RuntimeException("downstream failure"); })
                .when(filterChain).doFilter(any(), any());

        try {
            filter.doFilter(request, response, filterChain);
        } catch (Exception ignored) {
            // expected — asserting MDC cleanup below, not exception propagation
        }

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void filterChainInvokedExactlyOnce() throws Exception {
        MockHttpServletRequest  request  = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}
