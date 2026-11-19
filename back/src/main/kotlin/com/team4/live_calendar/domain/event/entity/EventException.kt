package com.team4.live_calendar.domain.event.entity

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

// 반복 일정의 특정 회차 예외 (삭제 또는 수정). recurrenceId는 원래 회차의 시작 일시
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "event_exceptions")
class EventException protected constructor(
    event: Event,
    recurrenceId: Instant,
    type: EventExceptionType,
    title: String?,
    description: String?,
    location: String?,
    startAt: Instant?,
    endAt: Instant?,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    var event: Event = event
        protected set

    @Column(name = "recurrence_id", nullable = false, updatable = false)
    var recurrenceId: Instant = recurrenceId
        protected set

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type", nullable = false, length = 20)
    var type: EventExceptionType = type
        protected set

    @Column(name = "title", length = 200)
    var title: String? = title
        protected set

    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = description
        protected set

    @Column(name = "location", length = 255)
    var location: String? = location
        protected set

    @Column(name = "start_at")
    var startAt: Instant? = startAt
        protected set

    @Column(name = "end_at")
    var endAt: Instant? = endAt
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
            event: Event,
            recurrenceId: Instant,
            type: EventExceptionType,
            title: String? = null,
            description: String? = null,
            location: String? = null,
            startAt: Instant? = null,
            endAt: Instant? = null,
        ): EventException =
            EventException(
                event = event,
                recurrenceId = recurrenceId,
                type = type,
                title = title?.trim(),
                description = description,
                location = location,
                startAt = startAt,
                endAt = endAt,
            )
    }
}
