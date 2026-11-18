package com.team4.live_calendar.domain.event.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
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
    calendarId: Long,
    categoryId: Long?,
    userId: Long,
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

    @Column(name = "calendar_id", nullable = false, updatable = false)
    var calendarId: Long = calendarId
        protected set

    @Column(name = "category_id")
    var categoryId: Long? = categoryId
        protected set

    @Column(name = "user_id", nullable = false, updatable = false)
    var userId: Long = userId
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

    // 일정 내용 수정 (반복 규칙, 시간대 변경은 반복 일정 단계에서 따로 추가)
    fun update(
        title: String,
        description: String?,
        location: String?,
        startAt: Instant,
        endAt: Instant,
        isAllDay: Boolean,
        categoryId: Long?,
    ) {
        this.title = title.trim()
        this.description = description
        this.location = location
        this.startAt = startAt
        this.endAt = endAt
        this.isAllDay = isAllDay
        this.categoryId = categoryId
    }

    companion object {
        const val DEFAULT_TIMEZONE = "UTC"

        fun create(
            calendarId: Long,
            categoryId: Long?,
            userId: Long,
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
                calendarId = calendarId,
                categoryId = categoryId,
                userId = userId,
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
