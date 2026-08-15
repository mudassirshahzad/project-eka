package com.mudassirshahzad.eka.api.config;

import com.mudassirshahzad.eka.api.security.AuthorizationInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers {@link AuthorizationInterceptor} (ADR AZ01) against every {@code /api/v1/**} route.
 * No exclusion for {@code /api/v1/auth/**} is needed: {@link AuthorizationInterceptor} only acts
 * on methods carrying {@code @RequireRole}, and the login endpoint never carries one.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthorizationInterceptor authorizationInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authorizationInterceptor).addPathPatterns("/api/v1/**");
    }
}
