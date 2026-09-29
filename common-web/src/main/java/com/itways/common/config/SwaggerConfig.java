package com.itways.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.itways.security.annotation.AccountId;

@Configuration("swaggerConfig")
public class SwaggerConfig {

    static {
        // @AccountId is filled from the caller's verified token, never sent by
        // the client. Left visible, springdoc documented it as a required query
        // parameter on 29 operations and folded it into the request body on 15
        // more — so the published API told clients to send their own tenant,
        // and types generated from it had the wrong body shape.
        SpringDocUtils.getConfig().addAnnotationsToIgnore(AccountId.class);
    }

    @Bean
    public AnyValueSchemas anyValueSchemas() {
        return new AnyValueSchemas();
    }

    @Bean
    public PrimitiveFieldsRequired primitiveFieldsRequired() {
        return new PrimitiveFieldsRequired();
    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI Assistant API Platform")
                        .version("1.0")
                        .description("Microservices Architecture for AI Assistant Platform"))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .name("bearerAuth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
