package com.team4.live_calendar.global.security

import org.springframework.boot.context.properties.ConfigurationProperties

// 인증 쿠키 설정. 기본값은 운영 기준(secure = true)이고 local·test 프로필에서만 끈다
@ConfigurationProperties(prefix = "app.auth.cookie")
data class AuthCookieProperties(
    val secure: Boolean = true,
)