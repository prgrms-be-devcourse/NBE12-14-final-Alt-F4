package com.team4.live_calendar.global.exception

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(e: BusinessException): ResponseEntity<ProblemDetail> {
        log.debug("Business exception: {}", e.errorCode, e)
        return toResponse(e.errorCode, e.message)
    }

    // Security 필터의 인증 실패(토큰 없음·만료·위조)도 EntryPoint를 거쳐 여기로 와서 같은 형식으로 응답한다
    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(e: AuthenticationException): ResponseEntity<ProblemDetail> {
        log.debug("Authentication failed: {}", e.message)
        return toResponse(ErrorCode.UNAUTHORIZED)
    }

    // 이 핸들러가 없으면 AccessDeniedException이 맨 아래 Exception 처리로 가서 500이 된다
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(e: AccessDeniedException): ResponseEntity<ProblemDetail> {
        log.debug("Access denied: {}", e.message)
        return toResponse(ErrorCode.FORBIDDEN)
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val problem = problemOf(ErrorCode.INVALID_INPUT).apply {
            setProperty(
                "errors",
                ex.bindingResult.fieldErrors.map {
                    mapOf("field" to it.field, "reason" to (it.defaultMessage ?: ""))
                },
            )
        }
        return ResponseEntity.status(problem.status).body(problem)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ProblemDetail> {
        log.error("Unhandled exception", e)
        return toResponse(ErrorCode.INTERNAL_SERVER_ERROR)
    }

    private fun toResponse(code: ErrorCode, detail: String = code.message) =
        ResponseEntity.status(code.status).body(problemOf(code, detail))

    private fun problemOf(code: ErrorCode, detail: String = code.message): ProblemDetail =
        ProblemDetail.forStatusAndDetail(code.status, detail).apply {
            title = code.status.reasonPhrase
            setProperty("code", code.name)
        }
}