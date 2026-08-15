package com.mudassirshahzad.eka.api.security;

import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
import com.mudassirshahzad.eka.domain.user.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit coverage for {@link AuthorizationInterceptor} (ADR AZ01/AZ02) — role-decision logic only,
 * in isolation from the full Spring MVC dispatch. Controller-level behavior (that
 * {@code @RequireRole} actually rejects an HTTP request with 403) is covered separately in
 * {@code ConversationControllerTest}.
 */
class AuthorizationInterceptorTest {

    private final AuthorizationInterceptor interceptor = new AuthorizationInterceptor();
    private final MockHttpServletRequest   request     = new MockHttpServletRequest();
    private final MockHttpServletResponse  response    = new MockHttpServletResponse();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void preHandle_nonHandlerMethod_isPermitted() {
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    void preHandle_noRequireRoleAnnotation_isPermitted() throws Exception {
        HandlerMethod handlerMethod = handlerMethodFor("unrestricted");

        assertThat(interceptor.preHandle(request, response, handlerMethod)).isTrue();
    }

    @Test
    void preHandle_requireRoleButNoAuthentication_throwsAccessDenied() throws Exception {
        HandlerMethod handlerMethod = handlerMethodFor("adminOnly");
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void preHandle_requireRoleButNonJwtAuthentication_throwsAccessDenied() throws Exception {
        HandlerMethod handlerMethod = handlerMethodFor("adminOnly");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("someone", "creds"));

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void preHandle_matchingRole_isPermitted() throws Exception {
        HandlerMethod handlerMethod = handlerMethodFor("adminOnly");
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                UserId.generate(), TenantId.generate(), List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        assertThat(interceptor.preHandle(request, response, handlerMethod)).isTrue();
    }

    @Test
    void preHandle_nonMatchingRole_throwsAccessDenied() throws Exception {
        HandlerMethod handlerMethod = handlerMethodFor("adminOnly");
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                UserId.generate(), TenantId.generate(), List.of(new SimpleGrantedAuthority("ROLE_VIEWER"))));

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void preHandle_multipleAllowedRoles_permitsAnyOfThem() throws Exception {
        HandlerMethod handlerMethod = handlerMethodFor("userOrAdmin");
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                UserId.generate(), TenantId.generate(), List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        assertThat(interceptor.preHandle(request, response, handlerMethod)).isTrue();
    }

    private HandlerMethod handlerMethodFor(String methodName) throws NoSuchMethodException {
        Method method = StubController.class.getMethod(methodName);
        return new HandlerMethod(new StubController(), method);
    }

    @SuppressWarnings("unused")
    static class StubController {
        public void unrestricted() {}

        @RequireRole(UserRole.ADMIN)
        public void adminOnly() {}

        @RequireRole({UserRole.USER, UserRole.ADMIN})
        public void userOrAdmin() {}
    }
}
