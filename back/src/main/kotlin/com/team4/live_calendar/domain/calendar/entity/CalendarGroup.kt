package com.team4.live_calendar.domain.calendar.entity

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

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "calendar_groups")
class CalendarGroup protected constructor(
    user: User,
    name: String,
    displayOrder: Int?,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    var user: User = user
        protected set

    @Column(name = "name", nullable = false, length = 100)
    var name: String = name
        protected set

    @Column(name = "display_order")
    var displayOrder: Int? = displayOrder
        protected set

    @Column(name = "is_visible")
    var isVisible: Boolean? = true
        protected set

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: Instant
        protected set

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: Instant
        protected set

    // 이름만 바꾼다 (순서·표시 여부는 따로)
    fun rename(name: String) {
        this.name = name.trim()
    }

    companion object {
        fun create(
            user: User,
            name: String,
            displayOrder: Int?,
        ): CalendarGroup =
            CalendarGroup(
                user = user,
                name = name.trim(),
                displayOrder = displayOrder,
            )
    }
}
