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
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

// 회원이 캘린더를 이용하는 관계 (소유, 참여, 구독 모두 포함)
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "subscribes")
class Subscribe protected constructor(
    user: User,
    group: CalendarGroup,
    calendar: Calendar,
    role: CalendarRole,
    color: String?,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    var user: User = user
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    var group: CalendarGroup = group
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calendar_id", nullable = false, updatable = false)
    var calendar: Calendar = calendar
        protected set

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "role", nullable = false, length = 20)
    var role: CalendarRole = role
        protected set

    @Column(name = "color", length = 30)
    var color: String? = color
        protected set

    @Column(name = "is_visible", nullable = false)
    var isVisible: Boolean = true
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
        fun create(
            user: User,
            group: CalendarGroup,
            calendar: Calendar,
            role: CalendarRole,
            color: String?,
        ): Subscribe =
            Subscribe(
                user = user,
                group = group,
                calendar = calendar,
                role = role,
                color = color,
            )
    }
}
