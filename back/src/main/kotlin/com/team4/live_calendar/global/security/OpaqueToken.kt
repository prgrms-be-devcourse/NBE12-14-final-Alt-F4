package com.team4.live_calendar.global.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*

// 추측할 수 없는 랜덤 토큰과 그 해시. 원문은 사용자에게 한 번만 주고 DB에는 해시만 저장한다
// 쓰는 곳: Refresh Token, 조율방 초대 링크
object OpaqueToken {

    private val secureRandom = SecureRandom()

    // 32바이트(256비트) 랜덤 → URL에 그대로 넣을 수 있는 Base64url 43자
    fun generate(): String {
        val bytes = ByteArray(32).also(secureRandom::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    // SHA-256 16진수 64자 (DB 컬럼 CHAR(64))
    fun hash(rawToken: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rawToken.toByteArray()))
}
