package com.team4.live_calendar.domain.auth.dto

class TokenResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
)