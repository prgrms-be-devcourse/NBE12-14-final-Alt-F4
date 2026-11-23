package com.team4.live_calendar.domain.event.entity

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.category.entity.EventCategory
import com.team4.live_calendar.domain.user.entity.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "events")
class Event protected constructor(
    calendar: Calendar,
    category: EventCategory?,
    user: User,
    title: String,
    description: String?,
    location: String?,
    startAt: Instant,
    endAt: Instant,
    isAllDay: Boolean,
    timezone: String,
    rrule: String?,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calendar_id", nullable = false, updatable = false)
    var calendar: Calendar = calendar
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    var category: EventCategory? = category
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    var user: User = user
        protected set

    @Column(name = "uid", nullable = false, updatable = false)
    var uid: String = UUID.randomUUID().toString()
        protected set

    @Column(name = "title", nullable = false, length = 200)
    var title: String = title
        protected set

    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = description
        protected set

    @Column(name = "location", length = 255)
    var location: String? = location
        protected set

    @Column(name = "start_at", nullable = false)
    var startAt: Instant = startAt
        protected set

    @Column(name = "end_at", nullable = false)
    var endAt: Instant = endAt
        protected set

    @Column(name = "is_all_day", nullable = false)
    var isAllDay: Boolean = isAllDay
        protected set

    @Column(name = "timezone", nullable = false, length = 100)
    var timezone: String = timezone
        protected set

    @Column(name = "rrule", length = 255)
    var rrule: String? = rrule
        protected set

    @Column(name = "recur_until")
    var recurUntil: Instant? = null
        protected set

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant
        protected set

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: Instant
        protected set

    // 일정 내용 수정 (반복 규칙, 시간대는 changeRecurrence로 바꾼다)
    fun update(
        title: String,
        description: String?,
        location: String?,
        startAt: Instant,
        endAt: Instant,
        isAllDay: Boolean,
        category: EventCategory?,
    ) {
        this.title = title.trim()
        this.description = description
        this.location = location
        this.startAt = startAt
        this.endAt = endAt
        this.isAllDay = isAllDay
        this.category = category
    }

    // 반복 규칙과 시간대 변경. rrule이 null이면 반복 없음 (recurUntil도 null)
    fun changeRecurrence(timezone: String, rrule: String?, recurUntil: Instant?) {
        this.timezone = timezone
        this.rrule = rrule
        this.recurUntil = recurUntil
    }

    companion object {
        const val DEFAULT_TIMEZONE = "UTC"

        fun create(
            calendar: Calendar,
            category: EventCategory?,
            user: User,
            title: String,
            description: String?,
            location: String?,
            startAt: Instant,
            endAt: Instant,
            isAllDay: Boolean = false,
            timezone: String = DEFAULT_TIMEZONE,
            rrule: String? = null,
        ): Event =
            Event(
                calendar = calendar,
                category = category,
                user = user,
                title = title.trim(),
                description = description,
                location = location,
                startAt = startAt,
                endAt = endAt,
                isAllDay = isAllDay,
                timezone = timezone,
                rrule = rrule,
            )
    }
}
