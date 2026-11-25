package com.team4.live_calendar.domain.auth.service

// 서비스 → 컨트롤러 전달용. Refresh Token 원문이 들어 있어 toString()이 값을 찍는 data class로 만들지 않는다
class AuthTokens(
    val accessToken: String,
    val accessTokenExpiresIn: Long,
    val refreshToken: String,
)