package com.mudassirshahzad.eka.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit coverage for {@link RequestSizeLimitFilter} (v0.6.1, ADR EX06): oversized requests are
 * rejected with 413 before reaching the filter chain; requests within the limit pass through
 * untouched.
 */
class RequestSizeLimitFilterTest {

    private static final long MAX_BODY_BYTES = 100;

    private final RequestSizeLimitFilter filter =
            new RequestSizeLimitFilter(MAX_BODY_BYTES, new ObjectMapper());

    @Test
    void contentLengthUnderLimit_passesThroughToChain() throws Exception {
        MockHttpServletRequest  request  = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setContent(new byte[50]);
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200); // MockHttpServletResponse default
    }

    @Test
    void contentLengthOverLimit_rejectedWithoutReachingChain() throws Exception {
        MockHttpServletRequest  request  = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setContent(new byte[200]);
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(filterChain);
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(response.getContentAsString()).contains("\"status\":413");
    }

    /**
     * P06.1, ADR PC01: a multipart request to the actual upload route is exempt from this
     * filter's Content-Length check — governed instead by
     * {@code spring.servlet.multipart.max-file-size}/{@code max-request-size}.
     */
    @Test
    void multipartRequestToUploadRoute_overJsonLimit_passesThroughToChain() throws Exception {
        MockHttpServletRequest  request  = new MockHttpServletRequest("POST", "/api/v1/documents");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setContentType("multipart/form-data; boundary=----WebKitFormBoundary");
        request.setContent(new byte[200]); // over MAX_BODY_BYTES, would be rejected if not exempt
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    /**
     * The exemption is path-scoped, not "any multipart content type" — a request merely claiming
     * to be multipart against a different endpoint gets no special treatment, closing the
     * bypass a content-type-only exemption would otherwise open.
     */
    @Test
    void multipartContentType_toUnrelatedPath_stillRejectedOverLimit() throws Exception {
        MockHttpServletRequest  request  = new MockHttpServletRequest("POST", "/api/v1/conversations");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setContentType("multipart/form-data; boundary=----WebKitFormBoundary");
        request.setContent(new byte[200]);
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(filterChain);
        assertThat(response.getStatus()).isEqualTo(413);
    }
}
