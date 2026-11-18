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
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "events")
@EntityListeners(AuditingEntityListener::class)
class Event(
    @Column(nullable = false, updatable = false)
    val calendarId: Long,

    var categoryId: Long?,

    @Column(nullable = false, updatable = false)
    val createdBy: Long,

    @Column(nullable = false, length = 200)
    var title: String,

    @Column(columnDefinition = "TEXT")
    var description: String?,

    var location: String?,

    @Column(nullable = false)
    var startAt: LocalDateTime,

    @Column(nullable = false)
    var endAt: LocalDateTime,

    @Column(nullable = false)
    var isAllDay: Boolean = false,

    @Column(nullable = false, length = 100)
    var timezone: String = "UTC", //기본 시간대 UTC

    var rrule: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false, updatable = false)
    val uid: String = UUID.randomUUID().toString()

    var recurUntil: LocalDateTime? = null
        protected set

    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: Instant? = null
        protected set

    @LastModifiedDate
    @Column(nullable = false)
    var updatedAt: Instant? = null
        protected set
}