package com.groceryecom.platform.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document served at /api/v3/api-docs and /api/swagger-ui.html.
 * Protected endpoints declare {@code @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)}.
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI groceryEcomOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("GroceryEcom API")
                        .description("Multi-vendor grocery marketplace")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }
}
