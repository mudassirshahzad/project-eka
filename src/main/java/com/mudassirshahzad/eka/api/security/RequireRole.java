package com.mudassirshahzad.eka.api.security;

import com.mudassirshahzad.eka.domain.user.UserRole;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares which {@link UserRole}s may invoke an endpoint — the sole way this codebase expresses
 * a role requirement (ADR AZ02). Absent on a method, any authenticated role is permitted; the
 * endpoint is still subject to whatever ownership check the application layer itself performs.
 * Enforced by {@link AuthorizationInterceptor}, never by the controller method itself.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    UserRole[] value();
}
