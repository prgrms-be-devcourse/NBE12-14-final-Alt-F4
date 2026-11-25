package com.team4.live_calendar.domain.auth.entity

import com.team4.live_calendar.domain.user.entity.User
import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

// 로그인 유지용 Refresh Token. 원문은 쿠키로만 보내고 DB에는 SHA-256 해시만 저장한다
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "refresh_tokens",
    uniqueConstraints = [UniqueConstraint(name = "uk_refresh_tokens_token_hash", columnNames = ["token_hash"])],
    indexes = [Index(name = "idx_refresh_tokens_user_id", columnList = "user_id")],
)
class RefreshToken protected constructor(
    user: User,
    tokenHash: String,
    expiresAt: Instant,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    var user: User = user
        protected set

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    var tokenHash: String = tokenHash
        protected set

    @Column(name = "expires_at", nullable = false, updatable = false)
    var expiresAt: Instant = expiresAt
        protected set

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant
        protected set

    // now를 받아서 테스트에서 원하는 시각을 넣을 수 있게 한다
    fun isExpired(now: Instant): Boolean = !expiresAt.isAfter(now)

    companion object {
        fun create(user: User, tokenHash: String, expiresAt: Instant): RefreshToken =
            RefreshToken(user = user, tokenHash = tokenHash, expiresAt = expiresAt)
    }
}