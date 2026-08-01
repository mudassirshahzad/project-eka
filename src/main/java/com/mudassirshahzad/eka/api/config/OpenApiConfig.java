package com.mudassirshahzad.eka.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ekaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Project EKA API")
                        .description("Enterprise Knowledge Assistant — retrieval-augmented generation API")
                        .version("v1"));
    }
}
