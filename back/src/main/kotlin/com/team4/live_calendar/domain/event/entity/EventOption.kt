package com.team4.live_calendar.domain.event.entity

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.calendar.entity.Subscribe
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
import jakarta.persistence.UniqueConstraint
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

// 이용자별 일정 표시 설정 (calendar는 원본 일정과 같은 캘린더인지 검증용)
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "event_options",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_event_options_subscribe_event",
            columnNames = ["subscribe_id", "event_id"],
        ),
    ],
)
class EventOption protected constructor(
    subscribe: Subscribe,
    event: Event,
    calendar: Calendar,
    isVisible: Boolean,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscribe_id", nullable = false, updatable = false)
    var subscribe: Subscribe = subscribe
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    var event: Event = event
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calendar_id", nullable = false, updatable = false)
    var calendar: Calendar = calendar
        protected set

    @Column(name = "is_visible", nullable = false)
    var isVisible: Boolean = isVisible
        protected set

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant
        protected set

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: Instant
        protected set

    // 내 화면에서 숨긴다 (줄이 보임으로 남아 있던 경우. 지금은 그런 줄을 만드는 곳이 없다)
    fun hide() {
        isVisible = false
    }

    companion object {
        fun create(
            subscribe: Subscribe,
            event: Event,
            calendar: Calendar,
            isVisible: Boolean = true,
        ): EventOption =
            EventOption(
                subscribe = subscribe,
                event = event,
                calendar = calendar,
                isVisible = isVisible,
            )
    }
}
