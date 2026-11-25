package com.team4.live_calendar.domain.calendar.entity

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

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "calendars")
class Calendar protected constructor(
    name: String,
    description: String?,
    color: String?,
    timezone: String,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(name = "name", nullable = false, length = 100)
    var name: String = name
        protected set

    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = description
        protected set

    @Column(name = "color", length = 30)
    var color: String? = color
        protected set

    @Column(name = "timezone", nullable = false, length = 100)
    var timezone: String = timezone
        protected set

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant
        protected set

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: Instant
        protected set

    // 원본 정보를 바꾼다 (OWNER만, 서비스에서 확인). 시간대는 서비스에서 검증한 값을 받는다
    fun update(
        name: String,
        description: String?,
        color: String?,
        timezone: String,
    ) {
        this.name = name.trim()
        this.description = description
        this.color = color
        this.timezone = timezone
    }

    companion object {
        const val DEFAULT_TIMEZONE = "UTC"

        fun create(
            name: String,
            description: String?,
            color: String?,
            timezone: String = DEFAULT_TIMEZONE,
        ): Calendar =
            Calendar(
                name = name.trim(),
                description = description,
                color = color,
                timezone = timezone,
            )
    }
}
