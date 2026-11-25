package com.team4.live_calendar.global.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerExceptionResolver

// 권한 없음(403): EntryPoint와 같은 방식으로 표준 헤더 + ProblemDetail 본문
@Component
class ProblemDetailAccessDeniedHandler(
    @Qualifier("handlerExceptionResolver") private val resolver: HandlerExceptionResolver,
) : AccessDeniedHandler {

    private val bearer = BearerTokenAccessDeniedHandler()

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException
    ) {
        bearer.handle(request, response, accessDeniedException)
        resolver.resolveException(request, response, null, accessDeniedException)
    }
}
