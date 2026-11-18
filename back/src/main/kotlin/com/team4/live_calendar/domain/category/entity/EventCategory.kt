package com.team4.live_calendar.domain.category.entity

import com.team4.live_calendar.domain.calendar.entity.Calendar
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

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "event_categories")
class EventCategory protected constructor(
    calendar: Calendar,
    name: String,
    color: String?,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calendar_id", nullable = false, updatable = false)
    var calendar: Calendar = calendar
        protected set

    @Column(name = "name", nullable = false, length = 100)
    var name: String = name
        protected set

    @Column(name = "color", length = 30)
    var color: String? = color
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
            calendar: Calendar,
            name: String,
            color: String?,
        ): EventCategory =
            EventCategory(
                calendar = calendar,
                name = name.trim(),
                color = color,
            )
    }
}
