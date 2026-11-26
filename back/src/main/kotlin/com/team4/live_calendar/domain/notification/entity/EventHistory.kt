package com.team4.live_calendar.domain.notification.entity

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.event.entity.Event
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
@Table(name = "event_histories")
class EventHistory protected constructor(
    calendar: Calendar,
    event: Event,
    actor: User,
    targetType: EventHistoryTargetType,
    recurrenceId: Instant?,
    actionType: EventHistoryActionType,
    eventTitle: String,
    beforeData: Map<String, Any?>?,
    afterData: Map<String, Any?>?,
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
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    var event: Event = event
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, updatable = false)
    var actor: User = actor
        protected set

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "target_type", nullable = false, length = 30)
    var targetType: EventHistoryTargetType = targetType
        protected set

    @Column(name = "recurrence_id")
    var recurrenceId: Instant? = recurrenceId
        protected set

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "action_type", nullable = false, length = 20)
    var actionType: EventHistoryActionType = actionType
        protected set

    @Column(name = "event_title", nullable = false, length = 200)
    var eventTitle: String = eventTitle
        protected set

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_data", columnDefinition = "JSON")
    var beforeData: Map<String, Any?>? = beforeData
        protected set

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_data", columnDefinition = "JSON")
    var afterData: Map<String, Any?>? = afterData
        protected set

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant
        protected set

    companion object {
        fun create(
            calendar: Calendar,
            event: Event,
            actor: User,
            targetType: EventHistoryTargetType,
            recurrenceId: Instant? = null,
            actionType: EventHistoryActionType,
            eventTitle: String,
            beforeData: Map<String, Any?>? = null,
            afterData: Map<String, Any?>? = null,
        ): EventHistory =
            EventHistory(
                calendar = calendar,
                event = event,
                actor = actor,
                targetType = targetType,
                recurrenceId = recurrenceId,
                actionType = actionType,
                eventTitle = eventTitle,
                beforeData = beforeData,
                afterData = afterData,
            )
    }
}