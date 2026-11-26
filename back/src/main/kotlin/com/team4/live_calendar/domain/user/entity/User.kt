package com.team4.live_calendar.domain.user.entity

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "users",
    uniqueConstraints = [UniqueConstraint(name = "uk_users_email", columnNames = ["email"])],
)
class User protected constructor(
    email: String?,
    passwordHash: String?,
    nickname: String,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(name = "email", length = 255)
    var email: String? = email
        protected set

    @Column(name = "password_hash", length = 255)
    var passwordHash: String? = passwordHash
        protected set

    @Column(name = "nickname", nullable = false, length = 50)
    var nickname: String = nickname
        protected set

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant
        protected set

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: Instant
        protected set

    companion object {
        fun signUp(email: String, passwordHash: String, nickname: String): User =
            User(
                email = normalizeEmail(email),
                passwordHash = passwordHash,
                nickname = nickname.trim(),
            )

        fun normalizeEmail(email: String): String = email.trim().lowercase()
    }
}
