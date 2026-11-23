package com.team4.live_calendar.global.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerExceptionResolver

// 인증 실패(401): 표준 헤더는 Spring 기본 구현에 맡기고, 본문은 전역 예외 핸들러의 ProblemDetail로 만든다
@Component
class ProblemDetailAuthenticationEntryPoint(
    @Qualifier("handlerExceptionResolver") private val resolver: HandlerExceptionResolver,
) : AuthenticationEntryPoint {

    private val bearer = BearerTokenAuthenticationEntryPoint()

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        // RFC 6750: 401 + WWW-Authenticate: Bearer error="invalid_token", ...
        bearer.commence(request, response, authException)
        resolver.resolveException(request, response, null, authException)
    }
}
