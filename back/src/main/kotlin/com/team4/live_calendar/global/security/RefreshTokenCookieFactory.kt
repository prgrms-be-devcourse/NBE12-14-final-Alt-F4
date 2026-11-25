package com.team4.live_calendar.global.security

import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

// Refresh Token을 담는 쿠키를 만든다. 쿠키 옵션은 이 클래스 한 곳에서만 정한다
@Component
class RefreshTokenCookieFactory(
    private val jwtProperties: JwtProperties,
    private val cookieProperties: AuthCookieProperties,
) {
    fun create(rawToken: String): ResponseCookie =
        base(rawToken).maxAge(jwtProperties.refreshTokenExpiry).build()

    // 브라우저 쿠키는 서버가 지울 수 없어서, 같은 이름·경로로 수명 0인 쿠키를 보낸다
    fun expire(): ResponseCookie =
        base("").maxAge(Duration.ZERO).build()

    private fun base(value: String) =
        ResponseCookie.from(COOKIE_NAME, value)
            .httpOnly(true)
            .secure(cookieProperties.secure)
            .sameSite("Lax")
            .path("/api/v1/auth")

    companion object {
        const val COOKIE_NAME = "refresh_token"
    }
}