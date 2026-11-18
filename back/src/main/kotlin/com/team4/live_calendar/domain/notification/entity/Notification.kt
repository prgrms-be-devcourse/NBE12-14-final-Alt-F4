package com.team4.live_calendar.domain.notification.entity

import com.team4.live_calendar.domain.calendar.entity.Calendar
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

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "notifications")
class Notification protected constructor(
    user: User,
    calendar: Calendar?,
    type: NotificationType,
    params: Map<String, Any?>?,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    var user: User = user
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calendar_id")
    var calendar: Calendar? = calendar
        protected set

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type", nullable = false, length = 50)
    var type: NotificationType = type
        protected set

    // 알림 문구에 들어갈 값 (예: {"calendarName": "알고리즘 스터디"})
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params")
    var params: Map<String, Any?>? = params
        protected set

    @Column(name = "is_read", nullable = false)
    var isRead: Boolean = false
        protected set

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant
        protected set

    companion object {
        fun create(
            user: User,
            calendar: Calendar?,
            type: NotificationType,
            params: Map<String, Any?>? = null,
        ): Notification =
            Notification(
                user = user,
                calendar = calendar,
                type = type,
                params = params,
            )
    }
}
