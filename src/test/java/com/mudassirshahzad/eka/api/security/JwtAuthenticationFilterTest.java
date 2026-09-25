package com.mudassirshahzad.eka.api.security;

import com.mudassirshahzad.eka.application.auth.SessionApplicationService;
import com.mudassirshahzad.eka.domain.auth.SessionId;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
import io.jsonwebtoken.JwtException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtTokenProvider          jwtTokenProvider;
    @Mock private FilterChain               filterChain;
    @Mock private SessionApplicationService sessionApplicationService;

    private SimpleMeterRegistry     meterRegistry;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        filter = new JwtAuthenticationFilter(jwtTokenProvider, meterRegistry, sessionApplicationService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validBearerToken_populatesSecurityContext() throws Exception {
        JwtAuthenticationToken token = new JwtAuthenticationToken(UserId.generate(), TenantId.generate(), List.of(), SessionId.generate());
        when(jwtTokenProvider.parseToken("valid-token")).thenReturn(token);
        when(sessionApplicationService.isSessionActive(token.sessionId())).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(token);
        verify(filterChain).doFilter(eq(request), eq(response));
    }

    @Test
    void missingAuthorizationHeader_leavesContextEmpty() throws Exception {
        MockHttpServletRequest  request  = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void headerWithoutBearerPrefix_leavesContextEmpty() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void invalidToken_clearsContextAndContinuesChain() throws Exception {
        when(jwtTokenProvider.parseToken("bad-token")).thenThrow(new JwtException("boom"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getStatus()).isEqualTo(200); // filter itself never writes a response (ADR A04)
        verify(filterChain).doFilter(any(), any());
        assertThat(meterRegistry.get("eka.auth.failures").tag("type", "token").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void validSignatureButRevokedSession_leavesContextEmptyAndCountsASessionFailure() throws Exception {
        JwtAuthenticationToken token = new JwtAuthenticationToken(
                UserId.generate(), TenantId.generate(), List.of(), SessionId.generate());
        when(jwtTokenProvider.parseToken("revoked-session-token")).thenReturn(token);
        when(sessionApplicationService.isSessionActive(token.sessionId())).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer revoked-session-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        // This is the guarantee WP-2 exists for: the signature is still perfectly valid, but the
        // token is no longer accepted because its session was revoked before it expired.
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(meterRegistry.get("eka.auth.failures").tag("type", "session").counter().count())
                .isEqualTo(1.0);
        verify(filterChain).doFilter(eq(request), eq(response));
    }
}
