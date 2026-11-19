package com.team4.live_calendar.global.security

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "jwt")
class JwtProperties(
    val secret: String,
    val issuer: String,
    val accessTokenExpiry: Duration,
    val refreshTokenExpiry: Duration,
)