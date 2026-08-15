package com.mudassirshahzad.eka.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    /**
     * Declares the Bearer/JWT scheme so Swagger UI's "Authorize" button can attach a token to
     * every request it sends (P05.2 — every endpoint except {@code /api/v1/auth/login} now
     * requires one; see {@code SecurityConfig}).
     */
    @Bean
    public OpenAPI ekaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Project EKA API")
                        .description("Enterprise Knowledge Assistant — retrieval-augmented generation API")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
