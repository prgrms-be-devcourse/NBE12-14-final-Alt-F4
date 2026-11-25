package com.team4.live_calendar.domain.auth.service

import com.team4.live_calendar.domain.auth.entity.RefreshToken
import com.team4.live_calendar.domain.auth.repository.RefreshTokenRepository
import com.team4.live_calendar.domain.user.entity.User
import com.team4.live_calendar.global.exception.BusinessException
import com.team4.live_calendar.global.exception.ErrorCode
import com.team4.live_calendar.global.security.JwtProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.*

// Refresh Token 발급·사용(Rotation)·폐기. 원문은 돌려주기만 하고 DB에는 해시만 저장한다
@Service
class RefreshTokenService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtProperties: JwtProperties,
) {
    private val secureRandom = SecureRandom()

    @Transactional
    fun issue(user: User): String {
        val rawToken = generateRawToken()
        refreshTokenRepository.save(
            RefreshToken.create(
                user = user,
                tokenHash = hash(rawToken),
                expiresAt = Instant.now().plus(jwtProperties.refreshTokenExpiry),
            ),
        )
        return rawToken
    }

    // 돌려주는 User는 영속성 컨텍스트에서 분리된 LAZY 프록시라 id만 쓸 수 있다
    // (email 등을 읽으면 LazyInitializationException)
    @Transactional
    fun consume(rawToken: String): User {
        val tokenHash = hash(rawToken)
        val token = refreshTokenRepository.findByTokenHash(tokenHash)
            ?: throw BusinessException(ErrorCode.INVALID_REFRESH_TOKEN)

        // 예외로 롤백되므로 만료된 행은 지워지지 않고 남는다 (정리 배치는 고도화)
        if (token.isExpired(Instant.now())) {
            throw BusinessException(ErrorCode.INVALID_REFRESH_TOKEN)
        }
        // 0이면 동시에 들어온 다른 요청이 먼저 썼다
        if (refreshTokenRepository.deleteByTokenHash(tokenHash) == 0) {
            throw BusinessException(ErrorCode.INVALID_REFRESH_TOKEN)
        }
        return token.user
    }

    @Transactional
    fun revoke(rawToken: String) {
        refreshTokenRepository.deleteByTokenHash(hash(rawToken))
    }

    private fun generateRawToken(): String {
        val bytes = ByteArray(32).also(secureRandom::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hash(rawToken: String): String =
        HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(rawToken.toByteArray()),
        )
}