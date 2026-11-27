package com.team4.live_calendar.global.security

import com.team4.live_calendar.global.config.CorsProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val corsProperties: CorsProperties,
    private val authenticationEntryPoint: ProblemDetailAuthenticationEntryPoint,
    private val accessDeniedHandler: ProblemDetailAccessDeniedHandler,
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            csrf { disable() }
            cors { }
            httpBasic { disable() }
            formLogin { disable() }
            logout { disable() }
            sessionManagement {
                sessionCreationPolicy = SessionCreationPolicy.STATELESS
            }
            authorizeHttpRequests {
                authorize(HttpMethod.POST, "/api/v1/auth/signup", permitAll)
                authorize(HttpMethod.POST, "/api/v1/auth/login", permitAll)
                authorize(HttpMethod.POST, "/api/v1/auth/refresh", permitAll)
                authorize(HttpMethod.POST, "/api/v1/auth/logout", permitAll)

                authorize("/actuator/health", permitAll)
                authorize("/actuator/info", permitAll)
                authorize("/actuator/prometheus", permitAll)

                authorize("/swagger-ui.html", permitAll)
                authorize("/swagger-ui/**", permitAll)
                authorize("/v3/api-docs/**", permitAll)

                // WebSocket 연결 입구: 브라우저는 여기에 토큰 헤더를 못 붙여서, 인증은 STOMP CONNECT에서 한다
                authorize("/ws", permitAll)

                authorize(anyRequest, authenticated)
            }

            // 토큰이 아예 없는 요청 등 필터 체인 전체의 인증·인가 실패
            exceptionHandling {
                authenticationEntryPoint = this@SecurityConfig.authenticationEntryPoint
                accessDeniedHandler = this@SecurityConfig.accessDeniedHandler
            }

            // Bearer 토큰 검증 실패(만료·위조)는 Resource Server가 따로 처리하므로 여기도 지정한다
            oauth2ResourceServer {
                authenticationEntryPoint = this@SecurityConfig.authenticationEntryPoint
                accessDeniedHandler = this@SecurityConfig.accessDeniedHandler
                jwt { }
            }

        }
        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder =
        PasswordEncoderFactories.createDelegatingPasswordEncoder()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowedOrigins = corsProperties.allowedOrigins
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
            maxAge = 3600
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }
}