package com.mudassirshahzad.eka.api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The central role-authorization decision point (ADR AZ01) — every {@code /api/v1/**} request
 * except {@code /api/v1/auth/**} passes through here before reaching a controller method.
 *
 * <p>Deliberately narrow: this class answers only "does the caller's role permit this
 * <em>operation</em>," via {@link RequireRole}. It never answers "does the caller own <em>this
 * specific resource</em>" — that question depends on data only the application layer has already
 * loaded, so answering it here would mean querying the same repository a second time (the exact
 * "duplicated authorization logic" this milestone was told to avoid). Ownership and tenant checks
 * stay where the resource is fetched (ADR TN01) — see {@code ConversationApplicationService}.
 *
 * <p>Runs as a {@link HandlerInterceptor}, not a {@code Filter}, specifically because a
 * {@code Filter} runs before Spring MVC resolves the target method, so it has no clean way to read
 * {@link RequireRole}. By the time {@link #preHandle} runs, {@link JwtAuthenticationFilter} has
 * already populated the {@code SecurityContext} (or Spring Security's own authorization stage has
 * already rejected an unauthenticated request to a protected path with 401) — this class only ever
 * has to decide role sufficiency, never identity.
 */
@Component
public class AuthorizationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                              @NonNull Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken)) {
            throw new AccessDeniedException("Authentication required");
        }

        Set<String> requiredAuthorities = Arrays.stream(requireRole.value())
                .map(role -> "ROLE_" + role.name())
                .collect(Collectors.toSet());

        boolean permitted = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(requiredAuthorities::contains);

        if (!permitted) {
            throw new AccessDeniedException("Role does not permit this operation");
        }

        return true;
    }
}
