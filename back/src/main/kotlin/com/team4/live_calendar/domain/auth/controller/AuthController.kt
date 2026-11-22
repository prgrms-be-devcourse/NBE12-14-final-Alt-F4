package com.team4.live_calendar.domain.auth.controller

import com.team4.live_calendar.domain.auth.dto.LoginRequest
import com.team4.live_calendar.domain.auth.dto.SignupRequest
import com.team4.live_calendar.domain.auth.dto.SignupResponse
import com.team4.live_calendar.domain.auth.dto.TokenResponse
import com.team4.live_calendar.domain.auth.service.AuthService
import com.team4.live_calendar.domain.auth.service.AuthTokens
import com.team4.live_calendar.global.security.RefreshTokenCookieFactory
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

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

    private fun toTokenResponse(tokens: AuthTokens): ResponseEntity<TokenResponse> =
        ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookieFactory.create(tokens.refreshToken).toString())
            .body(TokenResponse(accessToken = tokens.accessToken, expiresIn = tokens.accessTokenExpiresIn))

}