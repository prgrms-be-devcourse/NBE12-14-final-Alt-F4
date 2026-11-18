package com.team4.live_calendar.domain.calendar.entity

import com.team4.live_calendar.domain.user.entity.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

// 캘린더 초대 링크 (토큰 원문은 저장하지 않고 SHA-256 해시만 저장)
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "calendar_invitations")
class CalendarInvitation protected constructor(
    calendar: Calendar,
    user: User,
    tokenHash: String,
    role: CalendarRole,
    expiresAt: Instant,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calendar_id", nullable = false, updatable = false)
    var calendar: Calendar = calendar
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    var user: User = user
        protected set

    @Column(name = "token_hash", nullable = false, updatable = false, columnDefinition = "CHAR(64)")
    var tokenHash: String = tokenHash
        protected set

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "role", nullable = false, length = 20)
    var role: CalendarRole = role
        protected set

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant = expiresAt
        protected set

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    var status: InvitationStatus = InvitationStatus.ACTIVE
        protected set

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant
        protected set

    companion object {
        fun create(
            calendar: Calendar,
            user: User,
            tokenHash: String,
            expiresAt: Instant,
            role: CalendarRole = CalendarRole.VIEWER,
        ): CalendarInvitation =
            CalendarInvitation(
                calendar = calendar,
                user = user,
                tokenHash = tokenHash,
                role = role,
                expiresAt = expiresAt,
            )
    }
}
