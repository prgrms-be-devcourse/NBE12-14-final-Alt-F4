package com.team4.live_calendar.global.exception

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
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