package com.team4.live_calendar.domain.auth.controller

import com.team4.live_calendar.domain.auth.dto.LoginRequest
import com.team4.live_calendar.domain.auth.dto.SignupRequest
import com.team4.live_calendar.domain.auth.dto.SignupResponse
import com.team4.live_calendar.domain.auth.dto.TokenResponse
import com.team4.live_calendar.domain.auth.service.AuthService
import com.team4.live_calendar.domain.auth.service.AuthTokens
import com.team4.live_calendar.global.exception.BusinessException
import com.team4.live_calendar.global.exception.ErrorCode
import com.team4.live_calendar.global.security.RefreshTokenCookieFactory
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

// 인증 API는 토큰 없이 호출한다. Swagger가 Authorization 헤더를 붙이지 않게 전역 인증 표시를 비운다
@SecurityRequirements
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
    private val cookieFactory: RefreshTokenCookieFactory,
) {

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    fun signup(@Valid @RequestBody request: SignupRequest): SignupResponse =
        authService.signup(request)

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<TokenResponse> =
        toTokenResponse(authService.login(request))

    @PostMapping("/refresh")
    fun refresh(
        @CookieValue(RefreshTokenCookieFactory.COOKIE_NAME, required = false) refreshToken: String?,
    ): ResponseEntity<TokenResponse> {
        if (refreshToken.isNullOrBlank()) throw BusinessException(ErrorCode.INVALID_REFRESH_TOKEN)
        return toTokenResponse(authService.refresh(refreshToken))
    }

    @PostMapping("/logout")
    fun logout(
        @CookieValue(RefreshTokenCookieFactory.COOKIE_NAME, required = false) refreshToken: String?,
    ): ResponseEntity<Void> {
        if (!refreshToken.isNullOrBlank()) authService.logout(refreshToken)
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, cookieFactory.expire().toString())
            .build()
    }

    // 로그인·재발급 공통: Access Token은 본문, Refresh Token은 Set-Cookie로 나눈다
    private fun toTokenResponse(tokens: AuthTokens): ResponseEntity<TokenResponse> =
        ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookieFactory.create(tokens.refreshToken).toString())
            .body(TokenResponse(accessToken = tokens.accessToken, expiresIn = tokens.accessTokenExpiresIn))

}