package com.team4.live_calendar.global.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// Swagger UI의 Authorize 버튼: Access Token을 넣으면 모든 요청에 Authorization: Bearer 헤더를 붙인다
@Configuration
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI =
        OpenAPI()
            .components(
                Components().addSecuritySchemes(
                    BEARER_AUTH,
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"),
                ),
            )
            .addSecurityItem(SecurityRequirement().addList(BEARER_AUTH))

    companion object {
        const val BEARER_AUTH = "bearerAuth"
    }
}
