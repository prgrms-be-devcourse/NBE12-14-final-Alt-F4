package com.team4.live_calendar.domain.category.entity

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
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

// 이용자별 카테고리 표시 설정 (calendar는 원본 카테고리와 같은 캘린더인지 검증용)
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "category_options")
class CategoryOption protected constructor(
    subscribe: Subscribe,
    category: EventCategory,
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
    @JoinColumn(name = "category_id", nullable = false, updatable = false)
    var category: EventCategory = category
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

    companion object {
        fun create(
            subscribe: Subscribe,
            category: EventCategory,
            calendar: Calendar,
            isVisible: Boolean = true,
        ): CategoryOption =
            CategoryOption(
                subscribe = subscribe,
                category = category,
                calendar = calendar,
                isVisible = isVisible,
            )
    }
}
